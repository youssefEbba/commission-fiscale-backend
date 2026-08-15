package mr.gov.finances.sgci.service.archive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.LigneBulletinLiquidation;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.ReferentielTaxe;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeLigneTaxe;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.ReferentielTaxeRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.archive.ImportArchiveResultatDto;
import mr.gov.finances.sgci.web.dto.archive.LigneTaxeArchiveDto;
import mr.gov.finances.sgci.web.dto.archive.ReleveArchiveDto;
import mr.gov.finances.sgci.web.dto.archive.UtilisationArchiveDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reprise d'un relevé de crédit d'impôt issu de l'ancienne application.
 *
 * <p>Le relevé apporte le crédit et ses utilisations ; l'interface fournit le contexte qui en est
 * absent (entreprise, autorité contractante, convention, marché). Le relevé ne comportant aucune
 * demande de correction, l'import en crée une d'archive qui porte le rattachement du dossier et à
 * laquelle le certificat et le marché sont reliés.
 *
 * <p>L'opération se fait en deux temps — {@link #previsualiser} ne lit rien d'autre que le fichier
 * et n'écrit rien, {@link #importer} enregistre. Ce découpage est délibéré : les relevés comportent
 * des écarts de solde que l'opérateur doit voir avant de valider.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportArchiveCreditService {

    private static final String PREFIXE_NUMERO = "ARCHIVE-";

    private final ReleveArchiveParser parser;
    private final CertificatCreditRepository certificatRepository;
    private final UtilisationCreditRepository utilisationRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final MarcheRepository marcheRepository;
    private final ConventionRepository conventionRepository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final AutoriteContractanteRepository autoriteRepository;
    private final ReferentielTaxeRepository referentielTaxeRepository;
    private final TransfertCreditRepository transfertCreditRepository;

    // ------------------------------------------------------------------
    // Aperçu — aucune écriture
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ReleveArchiveDto previsualiser(MultipartFile fichier) {
        ReleveArchiveDto releve = parser.parse(fichier);

        rapprocherEntreprise(releve);
        rapprocherMarcheEtAutorite(releve);

        certificatRepository.findByNumero(numeroCertificat(releve)).ifPresent(existant -> {
            releve.setCertificatDejaImporteId(existant.getId());
            releve.setCertificatDejaImporteReference(
                    existant.getReference() != null ? existant.getReference() : existant.getNumero());
            releve.getAnomalies().add("Ce relevé a déjà été importé (certificat "
                    + releve.getCertificatDejaImporteReference() + "). Un nouvel import sera refusé.");
        });

        return releve;
    }

    /**
     * Rapproche l'entreprise par le NIF. Le relevé et la base ne cadrent pas les zéros de tête de la
     * même façon ({@code 90600034} contre {@code 0090600034}) : la comparaison les ignore.
     */
    private void rapprocherEntreprise(ReleveArchiveDto releve) {
        String nifReleve = normaliserNif(releve.getNif());
        if (nifReleve == null) {
            return;
        }
        entrepriseRepository.findAll().stream()
                .filter(e -> nifReleve.equals(normaliserNif(e.getNif())))
                .findFirst()
                .ifPresent(e -> {
                    releve.setEntrepriseRapprocheeId(e.getId());
                    releve.setEntrepriseRapprocheeRaisonSociale(e.getRaisonSociale());
                    releve.setEntrepriseRapprocheeSource("NIF");
                });
    }

    private String normaliserNif(String nif) {
        if (nif == null || nif.isBlank()) {
            return null;
        }
        String chiffres = nif.trim().replaceAll("[^0-9]", "").replaceFirst("^0+", "");
        return chiffres.isBlank() ? null : chiffres;
    }

    /**
     * Rapproche le marché sur sa référence, puis en déduit l'autorité contractante en remontant
     * la convention rattachée. Le relevé ne nomme pas l'autorité : c'est le marché qui la désigne.
     */
    private void rapprocherMarcheEtAutorite(ReleveArchiveDto releve) {
        String reference = normaliserReference(releve.getReferenceMarche());
        if (reference == null) {
            return;
        }
        marcheRepository.findAll().stream()
                .filter(m -> correspond(reference, m.getNumeroMarche()) || correspond(reference, m.getReference()))
                .findFirst()
                .ifPresent(m -> {
                    releve.setMarcheRapprocheId(m.getId());
                    releve.setMarcheRapprocheNumero(m.getNumeroMarche());
                    releve.setMarcheRapprocheIntitule(m.getIntitule());
                    if (m.getConvention() != null && m.getConvention().getAutoriteContractante() != null) {
                        AutoriteContractante ac = m.getConvention().getAutoriteContractante();
                        releve.setAutoriteRapprocheeId(ac.getId());
                        releve.setAutoriteRapprocheeNom(ac.getNom());
                    }
                });
    }

    /** Rapprochement souple : la référence du relevé est souvent un fragment de celle du marché. */
    private boolean correspond(String referenceReleve, String valeurBase) {
        String base = normaliserReference(valeurBase);
        return base != null && (base.contains(referenceReleve) || referenceReleve.contains(base));
    }

    private String normaliserReference(String valeur) {
        if (valeur == null) {
            return null;
        }
        String compact = valeur.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return compact.length() < 4 ? null : compact;   // trop court = rapprochement non fiable
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    @Transactional
    public ImportArchiveResultatDto importer(MultipartFile fichier, Long entrepriseId,
                                             Long autoriteContractanteId, Long conventionId, Long marcheId,
                                             boolean confirmerMalgreAnomalies, AuthenticatedUser user) {
        ReleveArchiveDto releve = parser.parse(fichier);

        Entreprise entreprise = entrepriseRepository.findById(entrepriseId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Entreprise non trouvée : " + entrepriseId));
        AutoriteContractante autorite = autoriteRepository.findById(autoriteContractanteId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Autorité contractante non trouvée : " + autoriteContractanteId));
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Convention non trouvée : " + conventionId));
        Marche marche = marcheId == null ? null
                : marcheRepository.findById(marcheId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Marché non trouvé : " + marcheId));

        String numero = numeroCertificat(releve);
        certificatRepository.findByNumero(numero).ifPresent(existant -> {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Ce relevé a déjà été importé (certificat " + numero + ").");
        });

        if (releve.getUtilisations().isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Aucune utilisation n'a pu être lue dans le relevé : import interrompu.");
        }
        if (!releve.getAnomalies().isEmpty() && !confirmerMalgreAnomalies) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le relevé comporte " + releve.getAnomalies().size()
                            + " anomalie(s). Confirmez explicitement pour importer malgré tout.");
        }

        // Le relevé ne porte aucune demande de correction : on en crée une d'archive, qui rattache
        // le dossier à son autorité contractante et à sa convention et rend le certificat visible
        // dans les écrans qui passent par la demande.
        DemandeCorrection demande = demandeCorrectionRepository.save(
                creerDemandeArchive(releve, entreprise, autorite, convention, numero));

        if (marche != null) {
            rattacherMarche(marche, demande, convention);
        }

        CertificatCredit certificat = creerCertificat(releve, entreprise, numero);
        certificat.setDemandeCorrection(demande);
        certificat = certificatRepository.save(certificat);

        TransfertCredit transfert = creerTransfertArchive(releve, certificat);

        Map<String, ReferentielTaxe> referentiel = referentielTaxeRepository.findAll().stream()
                .collect(Collectors.toMap(t -> t.getCodeTaxe().toUpperCase(),
                        Function.identity(), (a, b) -> a));

        int douanieres = 0;
        int interieures = 0;
        int lignesTaxe = 0;
        for (UtilisationArchiveDto u : releve.getUtilisations()) {
            if (u.isDouaniere()) {
                UtilisationDouaniere douaniere = creerUtilisationDouaniere(u, certificat, entreprise, referentiel);
                utilisationRepository.save(douaniere);   // cascade sur les lignes de bulletin
                lignesTaxe += douaniere.getLignes().size();
                douanieres++;
            } else {
                utilisationRepository.save(creerUtilisationInterieure(u, certificat, entreprise));
                interieures++;
            }
        }

        log.info("Import archive : certificat {} créé pour l'entreprise {} — {} utilisation(s) douanière(s), "
                        + "{} intérieure(s), {} ligne(s) de taxe, par {}",
                numero, entreprise.getId(), douanieres, interieures, lignesTaxe,
                user != null ? user.getUsername() : "système");

        return construireResultat(releve, certificat, entreprise, douanieres, interieures, lignesTaxe,
                autorite, marche, transfert);
    }

    /**
     * Demande de correction d'archive, support du rattachement du dossier repris.
     *
     * <p>Elle est créée directement dans son état final : le dossier a déjà été instruit dans
     * l'ancienne application, la faire repartir à zéro la ferait réapparaître dans les files
     * d'attente DGD / DGTCP / DGI / DGB comme s'il restait à traiter.
     */
    private DemandeCorrection creerDemandeArchive(ReleveArchiveDto releve, Entreprise entreprise,
                                                  AutoriteContractante autorite, Convention convention,
                                                  String numeroCertificat) {
        DemandeCorrection demande = new DemandeCorrection();
        demande.setNumero(numeroCertificat.replace(PREFIXE_NUMERO, "ARCHIVE-DC-"));
        demande.setReference(null);   // attribuée par ReferenceBackfillMigration
        demande.setStatut(StatutDemande.NOTIFIEE);
        demande.setAutoriteContractante(autorite);
        demande.setEntreprise(entreprise);
        demande.setConvention(convention);
        demande.setCreditInterieur(valeurOuZero(releve.getCreditInterieur()));
        demande.setCreditExterieur(valeurOuZero(releve.getCreditDouanier()));
        demande.setIntituleMarche(releve.getReferenceMarche());
        demande.setDateDepot(releve.getDateCredit());
        demande.setValidationDgd(true);
        demande.setValidationDgtcp(true);
        demande.setValidationDgi(true);
        demande.setValidationDgb(true);
        return demande;
    }

    /**
     * Matérialise le transfert du relevé en {@link TransfertCredit} déjà exécuté.
     *
     * <p>Le montant retenu est la <b>seule part réellement transférée</b> vers le crédit intérieur —
     * la composante TVA. Le reliquat de droits, mis à zéro à la clôture, n'est pas un transfert et
     * n'a donc pas à figurer ici.
     *
     * <p>L'enjeu dépasse la traçabilité : le contrôle d'éligibilité interdit les utilisations
     * douanières sur un certificat dont le transfert a été exécuté. Sans cet enregistrement, ce
     * garde-fou resterait inactif sur les dossiers repris.
     *
     * <p>Aucune écriture de stock de TVA déductible n'est créée : les soldes du relevé intègrent
     * déjà le transfert, en ajouter une reviendrait à compter deux fois le même montant.
     */
    private TransfertCredit creerTransfertArchive(ReleveArchiveDto releve, CertificatCredit certificat) {
        BigDecimal montant = releve.getTransfertCredit();
        if (montant == null || montant.signum() <= 0) {
            return null;   // relevé sans transfert
        }
        TransfertCredit transfert = TransfertCredit.builder()
                .certificatCredit(certificat)
                .montant(montant)
                .statut(StatutTransfert.TRANSFERE)   // opération déjà réalisée dans l'ancien système
                .dateDemande(releve.getDateCredit())
                .operationsDouaneCloturees(Boolean.TRUE)
                .build();
        return transfertCreditRepository.save(transfert);
    }

    /** Relie le marché choisi à la demande d'archive, en refusant d'en détourner un déjà rattaché. */
    private void rattacherMarche(Marche marche, DemandeCorrection demande, Convention convention) {
        if (marche.getDemandeCorrection() != null
                && !marche.getDemandeCorrection().getId().equals(demande.getId())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Le marché « " + marche.getNumeroMarche() + " » est déjà rattaché à un autre dossier.");
        }
        marche.setDemandeCorrection(demande);
        if (marche.getConvention() == null) {
            marche.setConvention(convention);
        }
        marcheRepository.save(marche);
    }

    /**
     * Crée le certificat à partir des soldes <b>déclarés</b> dans le relevé, sans recalcul.
     *
     * <p>C'est délibéré : l'archive est reprise en l'état. Un solde douanier nul y est normal —
     * seule la composante TVA du crédit douanier est transférable vers le crédit intérieur, le
     * reliquat de droits (DD, RS, PC, PSC, TCO) étant mis à zéro à la clôture du crédit.
     */
    private CertificatCredit creerCertificat(ReleveArchiveDto releve, Entreprise entreprise, String numero) {
        BigDecimal soldeCordon = valeurOuZero(releve.getSoldeDouanierDeclare());
        BigDecimal soldeTva = valeurOuZero(releve.getSoldeInterieurDeclare());

        CertificatCredit certificat = new CertificatCredit();
        certificat.setNumero(numero);
        certificat.setReference(null);   // attribuée par ReferenceBackfillMigration au redémarrage
        certificat.setEntreprise(entreprise);
        certificat.setMontantCordon(releve.getCreditDouanier());
        certificat.setMontantTVAInterieure(releve.getCreditInterieur());
        certificat.setMontantMarcheHt(releve.getMontantMarche());
        certificat.setSoldeCordon(soldeCordon);
        certificat.setSoldeTVA(soldeTva);
        certificat.setDateMiseEnPlace(releve.getDateCredit());
        // Un crédit encore pourvu reste exploitable ; épuisé, il est clos.
        certificat.setStatut(soldeCordon.signum() > 0 || soldeTva.signum() > 0
                ? StatutCertificat.OUVERT
                : StatutCertificat.CLOTURE);
        return certificat;
    }

    private UtilisationDouaniere creerUtilisationDouaniere(UtilisationArchiveDto u, CertificatCredit certificat,
                                                          Entreprise entreprise,
                                                          Map<String, ReferentielTaxe> referentiel) {
        UtilisationDouaniere d = new UtilisationDouaniere();
        d.setCertificatCredit(certificat);
        d.setEntreprise(entreprise);
        d.setStatut(StatutUtilisation.APUREE);   // utilisation historique, déjà consommée
        d.setMontant(u.getMontant());
        d.setDateDemande(u.getDate());
        d.setDateLiquidation(u.getDate());
        d.setNumeroDeclaration(u.getLibelle());
        d.setNumeroBulletin(u.getNumeroQuittance());
        d.setTotalPrisEnCharge(u.getTotalPrisEnCharge());
        d.setTotalAPayer(u.getTotalAPayer());
        d.setMontantDroits(valeurTaxe(u, "DD"));
        d.setMontantTVA(valeurTaxe(u, "TVA"));

        for (LigneTaxeArchiveDto l : u.getLignesTaxe()) {
            ReferentielTaxe taxe = referentiel.get(l.getCodeTaxe().toUpperCase());
            LigneBulletinLiquidation ligne = LigneBulletinLiquidation.builder()
                    .codeTaxe(l.getCodeTaxe())
                    .denominationTaxe(taxe != null ? taxe.getDenominationTaxe() : l.getCodeTaxe())
                    .typeLigne(TypeLigneTaxe.GLOBALE)
                    .valeurTaxe(l.getValeur())
                    .affectationEntreprise(AffectationTaxe.valueOf(l.getAffectation()))
                    .affectation(AffectationTaxe.valueOf(l.getAffectation()))   // archive : décision acquise
                    .utilisationDouaniere(d)
                    .build();
            d.getLignes().add(ligne);
        }
        return d;
    }

    private UtilisationTVAInterieure creerUtilisationInterieure(UtilisationArchiveDto u,
                                                               CertificatCredit certificat,
                                                               Entreprise entreprise) {
        UtilisationTVAInterieure i = new UtilisationTVAInterieure();
        i.setCertificatCredit(certificat);
        i.setEntreprise(entreprise);
        i.setStatut(StatutUtilisation.APUREE);
        i.setMontant(u.getMontant());
        i.setDateDemande(u.getDate());
        i.setDateLiquidation(u.getDate());
        i.setNumeroFacture(u.getLibelle());
        i.setCreditInterieurUtilise(u.getMontant());
        return i;
    }

    private ImportArchiveResultatDto construireResultat(ReleveArchiveDto releve, CertificatCredit certificat,
                                                       Entreprise entreprise, int douanieres, int interieures,
                                                       int lignesTaxe, AutoriteContractante autorite,
                                                       Marche marche, TransfertCredit transfert) {
        ImportArchiveResultatDto resultat = new ImportArchiveResultatDto();
        resultat.setCertificatId(certificat.getId());
        resultat.setCertificatNumero(certificat.getNumero());
        resultat.setCertificatReference(certificat.getReference());
        resultat.setCertificatStatut(certificat.getStatut().name());
        resultat.setEntrepriseId(entreprise.getId());
        resultat.setEntrepriseRaisonSociale(entreprise.getRaisonSociale());
        if (certificat.getDemandeCorrection() != null) {
            resultat.setDemandeCorrectionId(certificat.getDemandeCorrection().getId());
            resultat.setDemandeCorrectionNumero(certificat.getDemandeCorrection().getNumero());
            resultat.setConventionId(certificat.getDemandeCorrection().getConvention() != null
                    ? certificat.getDemandeCorrection().getConvention().getId() : null);
        }
        resultat.setAutoriteContractanteId(autorite != null ? autorite.getId() : null);
        resultat.setMarcheId(marche != null ? marche.getId() : null);
        if (transfert != null) {
            resultat.setTransfertCreditId(transfert.getId());
            resultat.setTransfertCreditMontant(transfert.getMontant());
        }
        resultat.setUtilisationsDouanieres(douanieres);
        resultat.setUtilisationsInterieures(interieures);
        resultat.setLignesTaxeCreees(lignesTaxe);
        resultat.setSoldeCordon(certificat.getSoldeCordon());
        resultat.setSoldeTVA(certificat.getSoldeTVA());
        resultat.setAnomalies(releve.getAnomalies());

        // Contexte choisi par l'opérateur, tracé dans le journal applicatif.
        if (autorite != null || marche != null) {
            log.info("Import archive : certificat {} rattaché au contexte autorité={} marché={}",
                    certificat.getNumero(),
                    autorite != null ? autorite.getNom() : "-",
                    marche != null ? marche.getNumeroMarche() : "-");
        }
        return resultat;
    }

    private BigDecimal valeurTaxe(UtilisationArchiveDto u, String code) {
        return u.getLignesTaxe().stream()
                .filter(l -> code.equalsIgnoreCase(l.getCodeTaxe()))
                .map(LigneTaxeArchiveDto::getValeur)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal valeurOuZero(BigDecimal valeur) {
        return valeur != null ? valeur : BigDecimal.ZERO;
    }

    /** Numéro déduit du relevé, qui sert aussi de garde contre le double import. */
    private String numeroCertificat(ReleveArchiveDto releve) {
        String base = releve.getNumeroCredit() != null && !releve.getNumeroCredit().isBlank()
                ? releve.getNumeroCredit().trim()
                : releve.getNomFichier();
        return PREFIXE_NUMERO + base;
    }

    /** Codes de taxe connus du référentiel, pour information à l'écran. */
    @Transactional(readOnly = true)
    public List<String> codesTaxeConnus() {
        return referentielTaxeRepository.findAll().stream()
                .map(ReferentielTaxe::getCodeTaxe)
                .sorted()
                .toList();
    }
}
