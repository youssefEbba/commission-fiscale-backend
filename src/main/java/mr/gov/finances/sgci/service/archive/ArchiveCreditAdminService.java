package mr.gov.finances.sgci.service.archive;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.LotImportArchiveCreditRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.AuditService;
import mr.gov.finances.sgci.service.DossierGedService;
import mr.gov.finances.sgci.web.dto.archive.ArchiveCreditAdminDtos.ArchiveCreditDto;
import mr.gov.finances.sgci.web.dto.archive.ArchiveCreditAdminDtos.RattachementRequest;
import mr.gov.finances.sgci.web.dto.archive.ArchiveCreditAdminDtos.SuppressionApercu;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Correction et suppression d'un crédit repris de l'archive, par l'administrateur.
 *
 * <p>Un crédit mal repris (mauvaise entreprise, autorité, convention ou marché) se corrige sur place,
 * ou se supprime définitivement pour être réimporté. La suppression efface <b>tout</b> ce qui s'y
 * rattache, y compris ce qui a été fait dans l'application depuis l'import : l'écran l'annonce avant
 * (aperçu chiffré) et le geste est tracé dans le journal d'audit avec son motif.
 *
 * <p>Aucune clé étrangère n'est en cascade en base : les feuilles que JPA ne supprime pas en cascade
 * (pièces, décisions, réponses aux rejets, quittances DGI, stock de TVA, échanges, historique) partent
 * d'abord par requêtes groupées ; le certificat est ensuite supprimé par JPA, qui emporte ses
 * utilisations, avenants, transfert, sous-traitance et clôture ; la demande d'archive vient en dernier.
 */
@Service
@RequiredArgsConstructor
public class ArchiveCreditAdminService {

    private final CertificatCreditRepository certificatRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final AutoriteContractanteRepository autoriteRepository;
    private final ConventionRepository conventionRepository;
    private final MarcheRepository marcheRepository;
    private final UtilisationCreditRepository utilisationRepository;
    private final LotImportArchiveCreditRepository lotCreditRepository;
    private final ImportArchiveLotService lotService;
    private final DossierGedService dossierGedService;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager em;

    // Sous-requêtes communes : le certificat est :c, sa demande d'archive :d.
    private static final String UTILISATIONS = "select u.id from UtilisationCredit u where u.certificatCredit.id = :c";
    private static final String TRANSFERTS = "select t.id from TransfertCredit t where t.certificatCredit.id = :c";

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ArchiveCreditDto detail(Long certificatId) {
        CertificatCredit certificat = creditArchive(certificatId);
        return toDto(certificat);
    }

    // ------------------------------------------------------------------
    // Correction du rattachement
    // ------------------------------------------------------------------

    @Transactional
    public ArchiveCreditDto corrigerRattachement(Long certificatId, RattachementRequest req, AuthenticatedUser user) {
        assertAdmin(user, req != null ? req.getMotif() : null);
        CertificatCredit certificat = creditArchive(certificatId);
        DemandeCorrection demande = certificat.getDemandeCorrection();
        if (demande == null) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Ce crédit n'a pas de demande d'archive à corriger.");
        }

        Entreprise entreprise = entrepriseRepository.findById(req.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Entreprise non trouvée : " + req.getEntrepriseId()));
        AutoriteContractante autorite = autoriteRepository.findById(req.getAutoriteContractanteId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Autorité contractante non trouvée : " + req.getAutoriteContractanteId()));
        Convention convention = conventionRepository.findById(req.getConventionId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Convention non trouvée : " + req.getConventionId()));
        if (!convention.relieeA(autorite.getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "La convention « " + convention.getReference() + " » n'est pas rattachée à l'autorité contractante choisie.");
        }
        Marche marche = req.getMarcheId() == null ? null : marcheRepository.findById(req.getMarcheId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Marché non trouvé : " + req.getMarcheId()));
        if (marche != null && marche.getDemandeCorrection() != null
                && !marche.getDemandeCorrection().getId().equals(demande.getId())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Le marché « " + marche.getNumeroMarche() + " » est déjà rattaché à un autre dossier.");
        }

        Map<String, Object> avant = rattachement(certificat);

        certificat.setEntreprise(entreprise);
        demande.setEntreprise(entreprise);
        demande.setGroupement(null);
        demande.setAutoriteContractante(autorite);
        demande.setConvention(convention);
        utilisationRepository.findByCertificatCreditId(certificatId).forEach(u -> u.setEntreprise(entreprise));

        Marche actuel = marcheRepository.findByDemandeCorrectionId(demande.getId()).orElse(null);
        if (actuel != null && (marche == null || !actuel.getId().equals(marche.getId()))) {
            actuel.setDemandeCorrection(null);
            marcheRepository.saveAndFlush(actuel);
        }
        if (marche != null) {
            marche.setDemandeCorrection(demande);
            if (marche.getConvention() == null) {
                marche.setConvention(convention);
            }
        }
        lotService.reporterRattachement(certificatId, entreprise, autorite, convention, marche);
        em.flush();

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("avant", avant);
        trace.put("apres", rattachement(certificat));
        auditService.log(AuditAction.ADMIN_CORRECTION, "CertificatCredit", String.valueOf(certificatId), trace, req.getMotif());
        return toDto(certificat);
    }

    // ------------------------------------------------------------------
    // Suppression définitive
    // ------------------------------------------------------------------

    @Transactional
    public void supprimer(Long certificatId, String motif, AuthenticatedUser user) {
        assertAdmin(user, motif);
        CertificatCredit certificat = creditArchive(certificatId);
        DemandeCorrection demande = certificat.getDemandeCorrection();
        Long d = demande != null ? demande.getId() : -1L;
        Marche marche = demande != null ? marcheRepository.findByDemandeCorrectionId(d).orElse(null) : null;
        boolean supprimerMarche = marche != null && lotService.marcheCreeParImport(certificatId, marche.getId());

        Map<String, Object> trace = new LinkedHashMap<>(rattachement(certificat));
        trace.put("reference", reference(certificat));
        trace.put("suppression", apercu(certificatId, d));
        trace.put("marcheSupprime", supprimerMarche);

        em.flush();
        Map<String, Object> p = Map.of("c", certificatId, "d", d);

        // 1. Réponses aux rejets temporaires, puis échanges avec la Commission
        executer("delete from RejetTempResponse r where r.decisionUtilisationCredit.id in "
                + "(select x.id from DecisionUtilisationCredit x where x.utilisationCredit.id in (" + UTILISATIONS + "))", p);
        executer("delete from RejetTempResponse r where r.decisionCertificatCredit.id in "
                + "(select x.id from DecisionCertificatCredit x where x.certificatCredit.id = :c)", p);
        executer("delete from RejetTempResponse r where r.decisionTransfertCredit.id in "
                + "(select x.id from DecisionTransfertCredit x where x.transfertCredit.id in (" + TRANSFERTS + "))", p);
        executer("delete from RejetTempResponse r where r.decisionCorrection.id in "
                + "(select x.id from DecisionCorrection x where x.demandeCorrection.id = :d)", p);
        String explicationsDuCredit = "e.certificatCredit.id = :c or e.demandeCorrection.id = :d"
                + " or e.utilisationCredit.id in (" + UTILISATIONS + ")";
        executer("delete from DemandeExplicationMessage m where m.demandeExplication.id in "
                + "(select e.id from DemandeExplication e where " + explicationsDuCredit + ")", p);
        // Sans sous-requête sur la table supprimée : MySQL refuse « delete … where id in (select … from
        // la même table) » (erreur 1093), que H2 accepte.
        executer("delete from DemandeExplication e where " + explicationsDuCredit, p);

        // 2. Historique et notifications (liés par type et identifiant, sans clé étrangère)
        for (String entite : List.of("HistoriqueStatut", "Notification")) {
            executer("delete from " + entite + " h where (h.entityType = 'CertificatCredit' and h.entityId = :c)"
                    + " or (h.entityType = 'DemandeCorrection' and h.entityId = :d)"
                    + " or (h.entityType = 'UtilisationCredit' and h.entityId in (" + UTILISATIONS + "))"
                    + " or (h.entityType = 'TransfertCredit' and h.entityId in (" + TRANSFERTS + "))"
                    + " or (h.entityType = 'ClotureCredit' and h.entityId in (select x.id from ClotureCredit x where x.certificatCredit.id = :c))"
                    + " or (h.entityType = 'SousTraitance' and h.entityId in (select x.id from SousTraitance x where x.certificatCredit.id = :c))"
                    + " or (h.entityType = 'Avenant' and h.entityId in (select x.id from Avenant x where x.certificatCredit.id = :c))", p);
        }

        // 3. Utilisations : décisions, pièces, quittances DGI, puis le stock de TVA du crédit
        executer("delete from DecisionUtilisationCredit x where x.utilisationCredit.id in (" + UTILISATIONS + ")", p);
        executer("delete from DocumentUtilisationCredit x where x.utilisationCredit.id in (" + UTILISATIONS + ")", p);
        executer("delete from QuittanceDgi x where x.utilisationCredit.id in (" + UTILISATIONS + ")", p);
        executer("delete from TvaDeductibleStock x where x.certificatCredit.id = :c", p);

        // 4. Transfert, avenants, sous-traitance, clôture : leurs pièces et décisions
        executer("delete from DecisionTransfertCredit x where x.transfertCredit.id in (" + TRANSFERTS + ")", p);
        executer("delete from DocumentTransfertCredit x where x.transfertCredit.id in (" + TRANSFERTS + ")", p);
        executer("delete from DocumentAvenant x where x.avenant.id in "
                + "(select a.id from Avenant a where a.certificatCredit.id = :c or a.demandeCorrection.id = :d)", p);
        executer("delete from Avenant a where a.demandeCorrection.id = :d and a.certificatCredit is null", p);
        executer("delete from DocumentSousTraitance x where x.sousTraitance.id in "
                + "(select s.id from SousTraitance s where s.certificatCredit.id = :c)", p);
        executer("delete from DocumentClotureCredit x where x.clotureCredit.id in "
                + "(select s.id from ClotureCredit s where s.certificatCredit.id = :c)", p);

        // 5. Le certificat : décisions et pièces ; la demande : réclamations
        executer("delete from DecisionCertificatCredit x where x.certificatCredit.id = :c", p);
        executer("delete from DocumentCertificatCredit x where x.certificatCredit.id = :c", p);
        executer("delete from ReclamationDemandeCorrection x where x.demandeCorrection.id = :d", p);

        // 6. GED et crédits de lot (qui redeviennent importables)
        dossierGedService.clearCertificatFromDossierIfPresent(certificatId);
        if (demande != null) {
            dossierGedService.deleteDossierForDemandeCorrectionIfPresent(d);
        }
        lotService.detacherCertificatSupprime(certificatId);
        em.flush();
        em.clear();

        // 7. Le certificat (JPA emporte utilisations, lignes de bulletin, quittances Trésor, avenants,
        //    transfert, sous-traitance, clôture), puis la demande d'archive et, s'il vient de l'import, le marché.
        em.remove(em.find(CertificatCredit.class, certificatId));
        em.flush();
        if (demande != null) {
            Marche m = marcheRepository.findByDemandeCorrectionId(d).orElse(null);
            if (m != null) {
                m.setDemandeCorrection(null);
                em.flush();
            }
            em.remove(em.find(DemandeCorrection.class, d));
            em.flush();
            if (m != null && supprimerMarche) {
                lotService.detacherMarcheSupprime(m.getId());
                executer("delete from DocumentMarche x where x.marche.id = :m", Map.of("m", m.getId()));
                executer("delete from HistoriqueStatut h where h.entityType = 'Marche' and h.entityId = :m", Map.of("m", m.getId()));
                em.flush();
                em.remove(em.find(Marche.class, m.getId()));
                em.flush();
            }
        }
        auditService.log(AuditAction.DELETE, "CertificatCredit", String.valueOf(certificatId), trace, motif);
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private CertificatCredit creditArchive(Long certificatId) {
        CertificatCredit certificat = certificatRepository.findById(certificatId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Certificat de crédit non trouvé : " + certificatId));
        if (!estArchive(certificat)) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Seul un crédit repris de l'archive (référence « " + ImportArchiveCreditService.PREFIXE_NUMERO
                            + "… ») peut être corrigé ou supprimé ici.");
        }
        return certificat;
    }

    static boolean estArchive(CertificatCredit c) {
        String p = ImportArchiveCreditService.PREFIXE_NUMERO;
        return (c.getReference() != null && c.getReference().startsWith(p))
                || (c.getNumero() != null && c.getNumero().startsWith(p));
    }

    private static void assertAdmin(AuthenticatedUser user, String motif) {
        if (user == null || user.getRole() != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Correction ou suppression d'un crédit d'archive réservée à l'administrateur système");
        }
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif est obligatoire.");
        }
    }

    private int executer(String jpql, Map<String, Object> parametres) {
        var requete = em.createQuery(jpql);
        parametres.forEach((k, v) -> {
            if (jpql.contains(":" + k)) {
                requete.setParameter(k, v);
            }
        });
        return requete.executeUpdate();
    }

    private long compter(String jpql, Long c, Long d) {
        var requete = em.createQuery(jpql, Long.class);
        if (jpql.contains(":c")) requete.setParameter("c", c);
        if (jpql.contains(":d")) requete.setParameter("d", d);
        return requete.getSingleResult();
    }

    private SuppressionApercu apercu(Long c, Long d) {
        return SuppressionApercu.builder()
                .utilisationsArchive(compter("select count(u) from UtilisationCredit u where u.certificatCredit.id = :c and u.origineArchiveLibelle is not null", c, d))
                .utilisationsApplication(compter("select count(u) from UtilisationCredit u where u.certificatCredit.id = :c and u.origineArchiveLibelle is null", c, d))
                .transferts(compter("select count(t) from TransfertCredit t where t.certificatCredit.id = :c", c, d))
                .avenants(compter("select count(a) from Avenant a where a.certificatCredit.id = :c or a.demandeCorrection.id = :d", c, d))
                .sousTraitances(compter("select count(s) from SousTraitance s where s.certificatCredit.id = :c", c, d))
                .clotures(compter("select count(s) from ClotureCredit s where s.certificatCredit.id = :c", c, d))
                .pieces(compter("select count(x) from DocumentCertificatCredit x where x.certificatCredit.id = :c", c, d)
                        + compter("select count(x) from DocumentUtilisationCredit x where x.utilisationCredit.id in (" + UTILISATIONS + ")", c, d)
                        + compter("select count(x) from DocumentTransfertCredit x where x.transfertCredit.id in (" + TRANSFERTS + ")", c, d)
                        + compter("select count(x) from DocumentAvenant x where x.avenant.certificatCredit.id = :c", c, d)
                        + compter("select count(x) from DocumentSousTraitance x where x.sousTraitance.certificatCredit.id = :c", c, d)
                        + compter("select count(x) from DocumentClotureCredit x where x.clotureCredit.certificatCredit.id = :c", c, d)
                        + compter("select count(x) from Document x where x.demandeCorrection.id = :d", c, d))
                .lignesStockTva(compter("select count(x) from TvaDeductibleStock x where x.certificatCredit.id = :c", c, d))
                .echanges(compter("select count(e) from DemandeExplication e where e.certificatCredit.id = :c or e.demandeCorrection.id = :d", c, d))
                .creditsDeLot(lotCreditRepository.findByCertificatId(c).size())
                .build();
    }

    private static String reference(CertificatCredit c) {
        return c.getReference() != null ? c.getReference() : c.getNumero();
    }

    private Map<String, Object> rattachement(CertificatCredit c) {
        DemandeCorrection d = c.getDemandeCorrection();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entrepriseId", c.getEntreprise() != null ? c.getEntreprise().getId() : null);
        m.put("autoriteContractanteId", d != null && d.getAutoriteContractante() != null ? d.getAutoriteContractante().getId() : null);
        m.put("conventionId", d != null && d.getConvention() != null ? d.getConvention().getId() : null);
        Marche marche = d != null ? marcheRepository.findByDemandeCorrectionId(d.getId()).orElse(null) : null;
        m.put("marcheId", marche != null ? marche.getId() : null);
        return m;
    }

    private ArchiveCreditDto toDto(CertificatCredit c) {
        DemandeCorrection d = c.getDemandeCorrection();
        Entreprise e = c.getEntreprise();
        AutoriteContractante ac = d != null ? d.getAutoriteContractante() : null;
        Convention conv = d != null ? d.getConvention() : null;
        Marche m = d != null ? marcheRepository.findByDemandeCorrectionId(d.getId()).orElse(null) : null;
        return ArchiveCreditDto.builder()
                .certificatId(c.getId())
                .reference(reference(c))
                .demandeCorrectionId(d != null ? d.getId() : null)
                .entrepriseId(e != null ? e.getId() : null)
                .entrepriseRaisonSociale(e != null ? e.getRaisonSociale() : null)
                .entrepriseNif(e != null ? e.getNif() : null)
                .autoriteContractanteId(ac != null ? ac.getId() : null)
                .autoriteContractanteNom(ac != null ? ac.getNom() : null)
                .conventionId(conv != null ? conv.getId() : null)
                .conventionReference(conv != null ? conv.getReference() : null)
                .conventionIntitule(conv != null ? conv.getIntitule() : null)
                .marcheId(m != null ? m.getId() : null)
                .marcheNumero(m != null ? m.getNumeroMarche() : null)
                .marcheCreeParImport(m != null && lotService.marcheCreeParImport(c.getId(), m.getId()))
                .suppression(apercu(c.getId(), d != null ? d.getId() : -1L))
                .build();
    }
}
