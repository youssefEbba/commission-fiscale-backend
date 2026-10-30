package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Avenant;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Dqe;
import mr.gov.finances.sgci.domain.entity.ModeleFiscal;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutAvenant;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.repository.AvenantRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentAvenantRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.ApplicationAvenantRequest;
import mr.gov.finances.sgci.web.dto.AvenantDto;
import mr.gov.finances.sgci.web.dto.CreateAvenantRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Le cycle de l'avenant (équipe, octobre 2026) : Déposé → En cours de traitement → Adopté →
 * Appliqué, ou Rejeté.
 *
 * <ol>
 *   <li>L'entreprise ou l'autorité contractante le dépose sur son crédit ouvert, avec ses pièces.</li>
 *   <li>La DGTCP le prend en charge pour l'examiner.</li>
 *   <li>Le Président le valide. Sans impact financier, il est adopté. Avec impact, le système crée
 *       la demande de correction en brouillon, pré-remplie ; l'autorité la complète et la soumet, et
 *       elle suit le circuit habituel. L'avenant n'est adopté qu'avec elle
 *       ({@link AvenantSuiviCorrection}).</li>
 *   <li>La DGTCP l'applique : le crédit passe {@code MODIFIE} et, pour un avenant financier, ses
 *       soldes sont recalculés — consommé conservé, restant remplacé.</li>
 * </ol>
 *
 * <p>La DGTCP rejette à l'examen, le Président à la validation. Une fois la correction créée, c'est
 * elle qui décide.
 */
@Service
@RequiredArgsConstructor
public class AvenantService {

    public static final String PREFIX_AVENANT = "AV";

    private static final Set<StatutCertificat> CREDIT_OUVERT = EnumSet.of(StatutCertificat.OUVERT, StatutCertificat.MODIFIE);
    private static final Set<Role> DEPOSANTS = EnumSet.of(Role.ENTREPRISE, Role.AUTORITE_CONTRACTANTE,
            Role.AUTORITE_UPM, Role.AUTORITE_UEP);
    /** Une utilisation dans l'un de ces statuts a déjà touché les soldes, ou n'y touchera jamais. */
    private static final Set<StatutUtilisation> UTILISATION_TERMINEE = EnumSet.of(StatutUtilisation.BROUILLON,
            StatutUtilisation.LIQUIDEE, StatutUtilisation.APUREE, StatutUtilisation.REJETEE, StatutUtilisation.CLOTUREE);

    private final AvenantRepository repository;
    private final CertificatCreditRepository certificatRepository;
    private final DemandeCorrectionRepository demandeRepository;
    private final UtilisationCreditRepository utilisationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CertificatCreditService certificatCreditService;
    private final ReferenceSequenceGenerator referenceSequenceGenerator;
    private final DossierGedService dossierGedService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final DocumentAvenantRepository documentAvenantRepository;
    private final DocumentRequirementValidator requirementValidator;

    // ── lectures ────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AvenantDto> findAll(AuthenticatedUser user) {
        return repository.findAll().stream()
                .filter(a -> certificatCreditService.canAccessCertificat(a.getCertificatCredit().getId(), user))
                .sorted(Comparator.comparing(Avenant::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AvenantDto> findByCertificat(Long certificatId, AuthenticatedUser user) {
        if (!certificatCreditService.canAccessCertificat(certificatId, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé : crédit hors périmètre");
        }
        return repository.findByCertificatCreditId(certificatId).stream()
                .sorted(Comparator.comparing(Avenant::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AvenantDto findById(Long id, AuthenticatedUser user) {
        return toDto(charger(id, user));
    }

    /** L'avenant, s'il est dans le périmètre de l'appelant : celui de son crédit. */
    @Transactional(readOnly = true)
    public Avenant charger(Long id, AuthenticatedUser user) {
        Avenant a = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Avenant non trouvé: " + id));
        if (!certificatCreditService.canAccessCertificat(a.getCertificatCredit().getId(), user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé : avenant hors périmètre");
        }
        return a;
    }

    public void verifierDepotPiece(Long id, String codeDocument, AuthenticatedUser user) {
    }

    // ── dépôt ───────────────────────────────────────────────────────────────────────────────

    @Transactional
    public AvenantDto deposer(CreateAvenantRequest request, AuthenticatedUser user) {
        Role role = user != null ? user.getRole() : null;
        if (!DEPOSANTS.contains(role)) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Un avenant est déposé par l'entreprise ou l'autorité contractante");
        }
        CertificatCredit certificat = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Crédit d'impôt non trouvé: " + request.getCertificatCreditId()));
        if (!certificatCreditService.canAccessCertificat(certificat.getId(), user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé : crédit hors périmètre");
        }
        if (!CREDIT_OUVERT.contains(certificat.getStatut())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Un avenant ne se dépose que sur un crédit ouvert (statut actuel : " + certificat.getStatut() + ")");
        }
        if (request.getType() == mr.gov.finances.sgci.domain.enums.TypeAvenant.FINANCIER) {
            BigDecimal ext = request.getCreditExterieurPropose();
            BigDecimal inte = request.getCreditInterieurPropose();
            if ((ext == null && inte == null) || (ext != null && ext.signum() < 0) || (inte != null && inte.signum() < 0)) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                        "Un avenant financier précise les crédits proposés (extérieur et intérieur, positifs ou nuls)");
            }
        }
        Avenant a = repository.save(Avenant.builder()
                .numero(referenceSequenceGenerator.next(PREFIX_AVENANT))
                .type(request.getType())
                .statut(StatutAvenant.DEPOSE)
                .motif(request.getMotif())
                .dateSignature(request.getDateSignature())
                .creditExterieurPropose(request.getCreditExterieurPropose())
                .creditInterieurPropose(request.getCreditInterieurPropose())
                .certificatCredit(certificat)
                .deposeParUtilisateurId(user.getUserId())
                .dateDepot(Instant.now())
                .build());
        AvenantDto dto = toDto(a);
        auditService.log(AuditAction.CREATE, "Avenant", String.valueOf(a.getId()), dto);
        notifier(a, "Nouvel avenant " + a.getNumero() + " déposé : à examiner par la DGTCP");
        return dto;
    }

    // ── instruction ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public AvenantDto prendreEnCharge(Long id, String motif, AuthenticatedUser user) {
        exigerActeur(user, Role.DGTCP, motif, "La prise en charge d'un avenant revient à la DGTCP");
        Avenant a = charger(id, user);
        exigerStatut(a, StatutAvenant.DEPOSE, "prendre en charge");
        // Les pièces se déposent tant que l'avenant est déposé : la prise en charge les clôt, c'est
        // donc là que les pièces rendues obligatoires dans la GED (MODIFICATION_CI) sont exigées.
        requirementValidator.assertRequiredDocumentsPresent(ProcessusDocument.MODIFICATION_CI,
                documentAvenantRepository.findByAvenantIdAndActifTrue(a.getId()).stream()
                        .map(d -> d.getCodeDocument())
                        .toList());
        a.setStatut(StatutAvenant.EN_COURS_TRAITEMENT);
        a.setExamineParId(user.getUserId());
        a.setDateExamen(Instant.now());
        return enregistrer(a, motif, "Avenant " + a.getNumero() + " pris en charge par la DGTCP : en attente de la validation du Président");
    }

    @Transactional
    public AvenantDto valider(Long id, String motif, AuthenticatedUser user) {
        exigerActeur(user, Role.PRESIDENT, motif, "La validation d'un avenant revient au Président");
        Avenant a = charger(id, user);
        exigerStatut(a, StatutAvenant.EN_COURS_TRAITEMENT, "valider");
        if (a.getDemandeCorrection() != null) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Avenant déjà validé : sa demande de correction suit son circuit");
        }
        a.setValideParId(user.getUserId());
        a.setDateValidation(Instant.now());
        if (!a.estFinancier()) {
            a.setStatut(StatutAvenant.ADOPTE);
            return enregistrer(a, motif, "Avenant " + a.getNumero() + " adopté : à appliquer par la DGTCP");
        }
        a.setDemandeCorrection(creerDemandeDeCorrection(a));
        return enregistrer(a, motif, "Avenant " + a.getNumero() + " validé : sa demande de correction est créée, "
                + "à compléter et soumettre par l'autorité contractante");
    }

    @Transactional
    public AvenantDto rejeter(Long id, String motif, AuthenticatedUser user) {
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Le motif du rejet est obligatoire");
        }
        Role role = user != null ? user.getRole() : null;
        if (role != Role.DGTCP && role != Role.PRESIDENT && role != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Un avenant est rejeté par la DGTCP à l'examen ou par le Président à la validation");
        }
        Avenant a = charger(id, user);
        boolean rejetable = a.getStatut() == StatutAvenant.DEPOSE
                || (a.getStatut() == StatutAvenant.EN_COURS_TRAITEMENT && a.getDemandeCorrection() == null);
        if (!rejetable) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, a.getDemandeCorrection() != null
                    ? "Rejet impossible : la demande de correction de l'avenant décide désormais de son sort"
                    : "Rejet impossible dans le statut " + a.getStatut());
        }
        if (role == Role.PRESIDENT && a.getStatut() != StatutAvenant.EN_COURS_TRAITEMENT) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Le Président se prononce après l'examen de la DGTCP");
        }
        a.setStatut(StatutAvenant.REJETE);
        a.setMotifRejet(motif.trim());
        return enregistrer(a, role == Role.ADMIN_SI ? motif : null, "Avenant " + a.getNumero() + " rejeté : " + motif.trim());
    }

    // ── application ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public AvenantDto appliquer(Long id, ApplicationAvenantRequest recap, AuthenticatedUser user) {
        if (user == null || user.getRole() != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "L'application d'un avenant revient à la DGTCP");
        }
        Avenant a = charger(id, user);
        exigerStatut(a, StatutAvenant.ADOPTE, "appliquer");
        CertificatCredit c = a.getCertificatCredit();
        if (!CREDIT_OUVERT.contains(c.getStatut())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le crédit doit être ouvert pour appliquer l'avenant (statut actuel : " + c.getStatut() + ")");
        }
        List<String> enCours = utilisationRepository.findByCertificatCreditId(c.getId()).stream()
                .filter(u -> !UTILISATION_TERMINEE.contains(u.getStatut()))
                .map(u -> u.getReference() != null ? u.getReference() : "#" + u.getId())
                .collect(Collectors.toList());
        if (!enCours.isEmpty()) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Application impossible : des utilisations sont en cours sur ce crédit (" + String.join(", ", enCours)
                            + "). Leurs montants ne sont pas encore dans les soldes ; attendez leur liquidation ou leur apurement.");
        }
        if (a.estFinancier() && c.getTransfertCredit() != null
                && c.getTransfertCredit().getStatut() == StatutTransfert.TRANSFERE) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Application impossible : la TVA à l'importation de ce crédit a été transférée");
        }

        a.setMontantCordonAvant(c.getMontantCordon());
        a.setMontantTVAInterieureAvant(c.getMontantTVAInterieure());
        a.setSoldeCordonAvant(c.getSoldeCordon());
        a.setSoldeTVAAvant(c.getSoldeTVA());
        a.setTvaImportationRestanteAvant(c.getTvaImportationDouane());
        if (a.estFinancier()) {
            certificatCreditService.appliquerRecapitulatifAvenant(c, recap);
        }
        // Le seul chemin vers MODIFIE : la transition manuelle reste interdite.
        c.setStatut(StatutCertificat.MODIFIE);
        certificatRepository.save(c);
        a.setMontantCordonApres(c.getMontantCordon());
        a.setMontantTVAInterieureApres(c.getMontantTVAInterieure());
        a.setSoldeCordonApres(c.getSoldeCordon());
        a.setSoldeTVAApres(c.getSoldeTVA());
        a.setTvaImportationRestanteApres(c.getTvaImportationDouane());
        a.setStatut(StatutAvenant.APPLIQUE);
        a.setAppliqueParId(user.getUserId());
        a.setDateApplication(Instant.now());
        return enregistrer(a, null, "Avenant " + a.getNumero() + " appliqué : le crédit est mis à jour");
    }

    // ── la demande de correction d'un avenant financier ────────────────────────────────────

    /**
     * Crée la demande de correction d'un avenant financier, en brouillon, pré-remplie depuis la
     * demande d'origine du crédit : même autorité, même titulaire, même convention, et les crédits
     * proposés. L'autorité la complète et la soumet. Elle n'est pas rattachée au marché : ce lien
     * un-pour-un reste celui de la demande d'origine.
     */
    private DemandeCorrection creerDemandeDeCorrection(Avenant a) {
        DemandeCorrection origine = a.getCertificatCredit().getDemandeCorrection();
        if (origine == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Ce crédit n'a pas de demande de correction d'origine : la correction de l'avenant ne peut pas être pré-remplie");
        }
        String intitule = origine.getMarche() != null && origine.getMarche().getIntitule() != null
                ? origine.getMarche().getIntitule() : origine.getIntituleMarche();
        DemandeCorrection d = DemandeCorrection.builder()
                .numero("DC-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .reference(referenceSequenceGenerator.next(ReferenceSequenceGenerator.PREFIX_DEMANDE_CORRECTION))
                .dateDepot(Instant.now())
                .statut(StatutDemande.BROUILLON)
                .intituleMarche((intitule != null ? intitule + " — " : "") + "avenant " + a.getNumero())
                .referenceAppelOffre(origine.getReferenceAppelOffre())
                .creditExterieur(nz(a.getCreditExterieurPropose()))
                .creditInterieur(nz(a.getCreditInterieurPropose()))
                .autoriteContractante(origine.getAutoriteContractante())
                .entreprise(origine.getEntreprise())
                .groupement(origine.getGroupement())
                .convention(origine.getConvention())
                .build();
        d.setModeleFiscal(ModeleFiscal.builder().demandeCorrection(d).build());
        d.setDqe(Dqe.builder().demandeCorrection(d).build());
        d = demandeRepository.save(d);
        dossierGedService.ensureCreatedForDemandeCorrection(d.getId());
        auditService.log(AuditAction.CREATE, "DemandeCorrection", String.valueOf(d.getId()),
                Map.of("avenantId", a.getId(), "reference", d.getReference()));
        return d;
    }

    // ── outils ──────────────────────────────────────────────────────────────────────────────

    /** Le rôle attendu, ou l'administrateur qui s'y substitue avec un motif. */
    private static void exigerActeur(AuthenticatedUser user, Role attendu, String motif, String refus) {
        Role role = user != null ? user.getRole() : null;
        if (role == attendu) {
            return;
        }
        if (role == Role.ADMIN_SI) {
            if (motif == null || motif.isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                        "Le motif est obligatoire pour une substitution administrative");
            }
            return;
        }
        throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, refus);
    }

    private static void exigerStatut(Avenant a, StatutAvenant attendu, String geste) {
        if (a.getStatut() != attendu) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Impossible de " + geste + " l'avenant : il est " + a.getStatut()
                            + " (attendu : " + attendu + ")");
        }
    }

    private AvenantDto enregistrer(Avenant a, String motifSubstitution, String message) {
        a = repository.save(a);
        AvenantDto dto = toDto(a);
        if (motifSubstitution != null && !motifSubstitution.isBlank()) {
            auditService.log(AuditAction.ADMIN_CORRECTION, "Avenant", String.valueOf(a.getId()), dto, motifSubstitution);
        } else {
            auditService.log(AuditAction.UPDATE, "Avenant", String.valueOf(a.getId()), dto);
        }
        notifier(a, message);
        return dto;
    }

    /** Le déposant, la DGTCP et le Président. */
    private void notifier(Avenant a, String message) {
        List<Long> ids = new ArrayList<>();
        if (a.getDeposeParUtilisateurId() != null) {
            ids.add(a.getDeposeParUtilisateurId());
        }
        Stream.of(Role.DGTCP, Role.PRESIDENT)
                .flatMap(r -> utilisateurRepository.findByRoleAndActifTrue(r).stream())
                .map(Utilisateur::getId)
                .forEach(ids::add);
        Map<String, Object> payload = new HashMap<>();
        payload.put("avenantId", a.getId());
        payload.put("statut", a.getStatut() != null ? a.getStatut().name() : null);
        notificationService.notifyUsers(ids.stream().filter(Objects::nonNull).distinct().toList(),
                NotificationType.MODIFICATION_STATUT_CHANGE, "Avenant", a.getId(), message, payload);
    }

    private AvenantDto toDto(Avenant a) {
        CertificatCredit c = a.getCertificatCredit();
        DemandeCorrection origine = c != null ? c.getDemandeCorrection() : null;
        DemandeCorrection d = a.getDemandeCorrection();
        return AvenantDto.builder()
                .id(a.getId())
                .numero(a.getNumero())
                .type(a.getType())
                .statut(a.getStatut())
                .motif(a.getMotif())
                .dateSignature(a.getDateSignature())
                .certificatCreditId(c != null ? c.getId() : null)
                .certificatNumero(c != null ? c.getNumero() : null)
                .certificatReference(c != null ? c.getReference() : null)
                .entrepriseId(c != null && c.getEntreprise() != null ? c.getEntreprise().getId() : null)
                .entrepriseNom(c != null && c.getEntreprise() != null ? c.getEntreprise().getRaisonSociale() : null)
                .autoriteContractanteId(origine != null && origine.getAutoriteContractante() != null ? origine.getAutoriteContractante().getId() : null)
                .autoriteContractanteNom(origine != null && origine.getAutoriteContractante() != null ? origine.getAutoriteContractante().getNom() : null)
                .deposeParUtilisateurId(a.getDeposeParUtilisateurId())
                .dateDepot(a.getDateDepot())
                .creditExterieurPropose(a.getCreditExterieurPropose())
                .creditInterieurPropose(a.getCreditInterieurPropose())
                .demandeCorrectionId(d != null ? d.getId() : null)
                .demandeCorrectionReference(d != null ? d.getReference() : null)
                .demandeCorrectionStatut(d != null ? d.getStatut() : null)
                .dateExamen(a.getDateExamen())
                .dateValidation(a.getDateValidation())
                .dateApplication(a.getDateApplication())
                .motifRejet(a.getMotifRejet())
                .montantCordonAvant(a.getMontantCordonAvant())
                .montantCordonApres(a.getMontantCordonApres())
                .montantTVAInterieureAvant(a.getMontantTVAInterieureAvant())
                .montantTVAInterieureApres(a.getMontantTVAInterieureApres())
                .soldeCordonAvant(a.getSoldeCordonAvant())
                .soldeCordonApres(a.getSoldeCordonApres())
                .soldeTVAAvant(a.getSoldeTVAAvant())
                .soldeTVAApres(a.getSoldeTVAApres())
                .tvaImportationRestanteAvant(a.getTvaImportationRestanteAvant())
                .tvaImportationRestanteApres(a.getTvaImportationRestanteApres())
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
