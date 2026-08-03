
package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Bailleur;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DecisionCertificatCredit;
import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Dqe;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.ModeleFiscal;
import mr.gov.finances.sgci.domain.entity.Permission;
import mr.gov.finances.sgci.domain.entity.ReferentielTaxe;
import mr.gov.finances.sgci.domain.entity.RolePermission;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.BailleurRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DecisionCertificatCreditRepository;
import mr.gov.finances.sgci.repository.DecisionCorrectionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.PermissionRepository;
import mr.gov.finances.sgci.repository.ReferentielTaxeRepository;
import mr.gov.finances.sgci.repository.RolePermissionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.service.DossierGedService;
import mr.gov.finances.sgci.service.ReferentielTypeDocumentService;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.time.Instant;

@Component
@Order(100)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final AutoriteContractanteRepository autoriteContractanteRepository;
    private final ConventionRepository conventionRepository;
    private final BailleurRepository bailleurRepository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final DecisionCorrectionRepository decisionCorrectionRepository;
    private final DecisionCertificatCreditRepository decisionCertificatCreditRepository;
    private final CertificatCreditRepository certificatCreditRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final MarcheRepository marcheRepository;
    private final DocumentRequirementRepository documentRequirementRepository;
    private final PermissionRepository permissionRepository;
    private final ReferentielTaxeRepository referentielTaxeRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final DossierGedService dossierGedService;
    private final ReferentielTypeDocumentService referentielTypeDocumentService;
    private final DocumentRequirementLegacyMigration documentRequirementLegacyMigration;
    private final NotificationSchemaMigration notificationSchemaMigration;
    private final EntrepriseLegacyGroupementMigration entrepriseLegacyGroupementMigration;
    private final ReferenceBackfillMigration referenceBackfillMigration;
    private final Environment environment;

    @Override
    public void run(String... args) {
        if (utilisateurRepository.findByUsername("admin").isEmpty()) {
            Utilisateur admin = Utilisateur.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin"))
                    .role(Role.ADMIN_SI)
                    .nomComplet("Administrateur SGCI")
                    .email(seedUserEmail("admin"))
                    .actif(true)
                    .build();
            utilisateurRepository.save(admin);
        }

        seedPermissions();
        seedRolePermissions();
        referentielTypeDocumentService.seedFromEnumIfEmpty();
        documentRequirementLegacyMigration.migrateIfNeeded();
        notificationSchemaMigration.migrateIfNeeded();
        // Avant tout seed / création d'entreprise : retire les colonnes NOT NULL obsolètes.
        entrepriseLegacyGroupementMigration.migrateIfNeeded();
        seedDocumentRequirements();
        seedDefaultUsers();
        seedReferentielTaxes();
        if (Boolean.TRUE.equals(environment.getProperty("app.seed.demande-correction.enabled", Boolean.class, Boolean.TRUE))) {
            seedDemandeCorrectionAdopteeDemo();
            seedDemandeCorrectionEnValidationPresidentDemo();
            seedCertificatEnValidationPresidentDemo();
            seedCertificatEnControleVisasDgdDgiDemo();
        }
        if (Boolean.TRUE.equals(environment.getProperty("app.seed.utilisation-credit-demo.enabled", Boolean.class, Boolean.TRUE))
                && Boolean.TRUE.equals(environment.getProperty("app.seed.demande-correction.enabled", Boolean.class, Boolean.TRUE))) {
            seedUtilisationCreditDemoScenarios();
        }
        if (Boolean.TRUE.equals(environment.getProperty("app.seed.certificat-ouvert.enabled", Boolean.class, Boolean.FALSE))) {
            seedCertificatOuvertDemo();
        }
        referenceBackfillMigration.migrateIfNeeded();
    }

    private void seedDocumentRequirements() {
        EnumSet<TypeFichierAutorise> all = EnumSet.allOf(TypeFichierAutorise.class);

        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "LETTRE_SAISINE", false,
                all, "Lettre de saisine", 1);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "PV_OUVERTURE", false,
                all, "PV d’ouverture des offres financières", 2);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "ATTESTATION_FISCALE", false,
                all, "Attestation fiscale de l’entreprise", 3);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "OFFRE_FISCALE", false,
                all, "Offre fiscale", 4);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "OFFRE_FINANCIERE", false,
                all, "Offre financière", 5);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "TABLEAU_MODELE", false,
                all, "Tableau modèle (nature, valeur, classification)", 6);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "DAO_DQE", false,
                all, "DAO + DQE", 7);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "LISTE_ITEMS", false,
                all, "Liste des items Excel (FR/AR)", 8);

        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "FEUILLE_EVALUATION_SIGNEE", false,
                all, "Feuille d’évaluation signée", 9);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "OFFRE_FISCALE_CORRIGEE", false,
                all, "Offre fiscale corrigée (visa DGD)", 10);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "OFFRE_CORRIGEE", false,
                all, "Offre corrigée (alias)", 11);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "CREDIT_EXTERIEUR", false,
                all, "Crédit d’impôt extérieur", 12);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "LETTRE_ADOPTION", false,
                all, "Lettre d’adoption (Président)", 13);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "CREDIT_INTERIEUR", false,
                all, "Crédit d’impôt intérieur (visa DGI)", 14);

        /* GED / exigences pièces : ne pas retirer — utilisées par mise en place, utilisations, etc. */
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, "LETTRE_SAISINE", false,
                all, "Lettre de saisine", 1);
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, "CONTRAT", false,
                all, "Contrat enregistré", 2);
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, "LETTRE_NOTIFICATION_CONTRAT", false,
                all, "Lettre de notification du marché", 3);
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, "CERTIFICAT_NIF", false,
                all, "Certificat NIF", 4);
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, "LETTRE_CORRECTION", false,
                all, "Lettre de correction", 5);
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, "CERTIFICAT_CREDIT_IMPOTS", false,
                all, "Certificat de crédit d’impôt", 6);

        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "DEMANDE_UTILISATION", false,
                all, "Demande d’utilisation", 1);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "ORDRE_TRANSIT", false,
                all, "Ordre de transit", 2);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "DECLARATION_DOUANE", false,
                all, "Déclaration en douane", 3);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "BULLETIN_LIQUIDATION", false,
                all, "Bulletin de liquidation", 4);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "FACTURE", false,
                all, "Facture commerciale", 5);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "CONNAISSEMENT", false,
                all, "Connaissement / LTA / LVI", 6);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "CERTIFICAT_CREDIT_IMPOTS_SYDONIA", false,
                all, "Copie du certificat (SYDONIA)", 7);

        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, "FACTURE", false,
                all, "Facture fournisseur", 1);
        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, "DECLARATION_TVA", false,
                all, "Déclaration TVA", 2);
        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, "DECOMPTE", false,
                all, "Décompte (selon cas)", 3);

        seedDocReq(ProcessusDocument.MODIFICATION_CI, "NOTE_SERVICE", false,
                all, "Note de service", 1);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, "JUSTIFICATIONS_LEGALES", false,
                all, "Justifications légales", 2);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, "LETTRES_MOTIVEES", false,
                all, "Lettres motivées", 3);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, "AVENANT_CONTRAT", false,
                all, "Avenant au contrat", 4);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, "LETTRES_AUTORITE_CONTRACTANTE", false,
                all, "Lettres de l’autorité contractante", 5);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, "DETAIL_CORRECTIONS_NECESSAIRES", false,
                all, "Détail des corrections nécessaires", 6);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, "DOCUMENTS_OFFICIELS", false,
                all, "Documents officiels", 7);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, "DECISION_COMMISSION", false,
                all, "Décision de la commission / validation formelle", 8);

        seedDocReq(ProcessusDocument.TRANSFERT_CREDIT, "DEMANDE_MOTIVEE_TRANSFERT", true,
                all, "Demande motivée", 1);
        seedDocReq(ProcessusDocument.TRANSFERT_CREDIT, "DECLARATION_CLOTURE_DOUANE", true,
                all, "Déclaration de clôture", 2);
        seedDocReq(ProcessusDocument.TRANSFERT_CREDIT, "JUSTIFICATIFS_CLOTURE_DOUANE", true,
                all, "Justificatifs de clôture douane", 3);

        seedDocReq(ProcessusDocument.SOUS_TRAITANCE, "CONTRAT_SOUS_TRAITANCE_ENREGISTRE", false,
                all, "Contrat de sous-traitance enregistré", 1);
        seedDocReq(ProcessusDocument.SOUS_TRAITANCE, "LETTRE_SOUS_TRAITANCE", false,
                all, "Lettre détaillant volumes, quantités et pouvoirs", 2);

        seedDocReq(ProcessusDocument.CLOTURE_CI, "LISTE_CREDITS_A_CLOTURER", false,
                all, "Liste des crédits à annuler ou clôturer", 1);

        seedDocReq(ProcessusDocument.CONVENTION, "CONVENTION_JOIGNED_DOCUMENT", false,
                all, "Convention: document joint", 1);
        seedDocReq(ProcessusDocument.PROJET, "AUTRE_DOCUMENT", false,
                all, "Projet: document joint", 1);
        seedDocReq(ProcessusDocument.MARCHE, "AUTRE_DOCUMENT", false,
                all, "Marché: document joint", 1);

        upgradeTransfertCreditDocumentsToObligatoires();
    }

    /** Bases déjà seedées avec obligatoire=false : passage à obligatoire pour le P9. */
    private void upgradeTransfertCreditDocumentsToObligatoires() {
        for (String code : java.util.List.of(
                "DEMANDE_MOTIVEE_TRANSFERT",
                "DECLARATION_CLOTURE_DOUANE",
                "JUSTIFICATIFS_CLOTURE_DOUANE")) {
            documentRequirementRepository.findByProcessusAndCodeDocument(ProcessusDocument.TRANSFERT_CREDIT, code)
                    .filter(req -> !Boolean.TRUE.equals(req.getObligatoire()))
                    .ifPresent(req -> {
                        req.setObligatoire(true);
                        documentRequirementRepository.save(req);
                    });
        }
    }

    private void seedDocReq(ProcessusDocument processus, String codeDocument, boolean obligatoire,
                            EnumSet<TypeFichierAutorise> typesAutorises, String description, Integer ordre) {
        if (processus == null || codeDocument == null) {
            return;
        }
        documentRequirementRepository.findByProcessusAndCodeDocument(processus, codeDocument)
                .ifPresentOrElse(
                        existing -> {
                            if (description != null && (existing.getDescription() == null || existing.getDescription().isBlank())) {
                                existing.setDescription(description);
                            }
                            if (ordre != null && existing.getOrdreAffichage() == null) {
                                existing.setOrdreAffichage(ordre);
                            }
                            documentRequirementRepository.save(existing);
                        },
                        () -> {
                            DocumentRequirement req = DocumentRequirement.builder()
                                    .processus(processus)
                                    .codeDocument(codeDocument)
                                    .obligatoire(obligatoire)
                                    .typesAutorises(typesAutorises != null ? typesAutorises : EnumSet.noneOf(TypeFichierAutorise.class))
                                    .description(description)
                                    .ordreAffichage(ordre)
                                    .build();
                            try {
                                documentRequirementRepository.save(req);
                            } catch (org.springframework.dao.DataIntegrityViolationException ignored) {
                                // Ligne déjà présente (legacy type_document ou concurrence au démarrage)
                            }
                        });
    }

    private static final String DEMO_CORRECTION_NUMERO_PREFIX = "DC-DEFAULT-";
    private static final String DEMO_CORRECTION_NUMERO = "DC-DEFAULT-DEMO";
    private static final String DEMO_MARCHE_NUMERO = "MP-DEMO-DEFAULT-VOIRIE";

    /** Dossier correction en attente de validation Président (4 visas commission, sans lettre d'adoption). */
    private static final String DEMO_PRESIDENT_DC_NUMERO = "DC-DEMO-PRESIDENT";
    private static final String DEMO_PRESIDENT_MP_NUMERO = "MP-DEMO-PRESIDENT";

    /** Certificat mise en place en attente de validation Président (visas DGD / DGTCP / DGI). */
    private static final String DEMO_PRESIDENT_CI_NUMERO = "CI-DEMO-PRESIDENT";

    /** Demande de correction adoptée dédiée au certificat {@value #DEMO_CI_VISAS_DGD_DGI_NUMERO}. */
    private static final String DEMO_CI_VISAS_DC_NUMERO = "DC-DEMO-CI-VISAS";
    private static final String DEMO_CI_VISAS_MP_NUMERO = "MP-DEMO-CI-VISAS";

    /** Certificat EN_CONTROLE avec visas DGD et DGI — montants DGTCP vides (saisie PATCH /montants). */
    private static final String DEMO_CI_VISAS_DGD_DGI_NUMERO = "CI-DEMO-VISAS-DGD-DGI";

    /**
     * Données de démo : au plus une demande adoptée (+ marché lié) pour l’entreprise NIF_DEFAULT.
     * Idempotent : aucune nouvelle ligne aux redémarrages si une demande {@code DC-DEFAULT-*} existe déjà pour cette entreprise.
     */
    private void seedDemandeCorrectionAdopteeDemo() {
        AutoriteContractante autoriteContractante = autoriteContractanteRepository.findByCode("AC_DEFAULT")
                .orElseThrow(() -> new IllegalStateException("Autorité Contractante par défaut manquante"));
        Entreprise entreprise = entrepriseRepository.findByNif("NIF_DEFAULT")
                .orElseThrow(() -> new IllegalStateException("Entreprise par défaut manquante"));
        Convention convention = conventionRepository.findByReference("CONV-DEFAULT")
                .orElseThrow(() -> new IllegalStateException("Convention par défaut manquante"));

        if (demandeCorrectionRepository.existsByEntreprise_IdAndNumeroStartingWith(entreprise.getId(), DEMO_CORRECTION_NUMERO_PREFIX)) {
            return;
        }

        Marche marche = Marche.builder()
                .numeroMarche(DEMO_MARCHE_NUMERO)
                .dateSignature(LocalDate.now())
                .montantContratHt(BigDecimal.valueOf(1000000))
                .statut(StatutMarche.EN_COURS)
                .convention(convention)
                .build();
        marche = marcheRepository.save(marche);

        DemandeCorrection demande = DemandeCorrection.builder()
                .numero(DEMO_CORRECTION_NUMERO)
                .dateDepot(Instant.now())
                .statut(StatutDemande.ADOPTEE)
                .autoriteContractante(autoriteContractante)
                .entreprise(entreprise)
                .convention(convention)
                .build();

        // Visas pré-positionnés (sauf Président)
        demande.setValidationDgd(true);
        demande.setValidationDgdDate(Instant.now());
        demande.setValidationDgdUserId(utilisateurRepository.findByUsername("dgd").map(Utilisateur::getId).orElse(null));
        demande.setValidationDgtcp(true);
        demande.setValidationDgtcpDate(Instant.now());
        demande.setValidationDgtcpUserId(utilisateurRepository.findByUsername("dgtcp").map(Utilisateur::getId).orElse(null));
        demande.setValidationDgi(true);
        demande.setValidationDgiDate(Instant.now());
        demande.setValidationDgiUserId(utilisateurRepository.findByUsername("dgi").map(Utilisateur::getId).orElse(null));

        demande.setValidationDgb(true);
        demande.setValidationDgbDate(Instant.now());
        demande.setValidationDgbUserId(utilisateurRepository.findByUsername("dgb").map(Utilisateur::getId).orElse(null));

        ModeleFiscal modeleFiscal = ModeleFiscal.builder()
                .demandeCorrection(demande)
                .build();
        Dqe dqe = Dqe.builder()
                .demandeCorrection(demande)
                .build();
        demande.setModeleFiscal(modeleFiscal);
        demande.setDqe(dqe);

        demande = demandeCorrectionRepository.save(demande);

        dossierGedService.ensureCreatedForDemandeCorrection(demande.getId());

        // Lier Marché <-> DemandeCorrection
        marche.setDemandeCorrection(demande);
        marche = marcheRepository.save(marche);
        demande.setMarche(marche);
        demandeCorrectionRepository.save(demande);
    }

    /**
     * Demande {@value #DEMO_PRESIDENT_DC_NUMERO} : statut {@link StatutDemande#EN_VALIDATION},
     * visas DGD / DGTCP / DGI / DGB en base, sans {@code LETTRE_ADOPTION} — test upload + validation Président.
     */
    private void seedDemandeCorrectionEnValidationPresidentDemo() {
        if (demandeCorrectionRepository.existsByNumero(DEMO_PRESIDENT_DC_NUMERO)) {
            return;
        }

        AutoriteContractante autoriteContractante = autoriteContractanteRepository.findByCode("AC_DEFAULT")
                .orElseThrow(() -> new IllegalStateException("Autorité Contractante par défaut manquante"));
        Entreprise entreprise = entrepriseRepository.findByNif("NIF_DEFAULT")
                .orElseThrow(() -> new IllegalStateException("Entreprise par défaut manquante"));
        Convention convention = conventionRepository.findByReference("CONV-DEFAULT")
                .orElseThrow(() -> new IllegalStateException("Convention par défaut manquante"));

        Marche marche = Marche.builder()
                .numeroMarche(DEMO_PRESIDENT_MP_NUMERO)
                .dateSignature(LocalDate.now())
                .montantContratHt(BigDecimal.valueOf(850_000))
                .statut(StatutMarche.EN_COURS)
                .convention(convention)
                .build();
        marche = marcheRepository.save(marche);

        DemandeCorrection demande = DemandeCorrection.builder()
                .numero(DEMO_PRESIDENT_DC_NUMERO)
                .dateDepot(Instant.now())
                .statut(StatutDemande.EN_VALIDATION)
                .autoriteContractante(autoriteContractante)
                .entreprise(entreprise)
                .convention(convention)
                .build();

        applyCommissionVisaFlags(demande);

        ModeleFiscal modeleFiscal = ModeleFiscal.builder().demandeCorrection(demande).build();
        Dqe dqe = Dqe.builder().demandeCorrection(demande).build();
        demande.setModeleFiscal(modeleFiscal);
        demande.setDqe(dqe);
        demande = demandeCorrectionRepository.save(demande);

        dossierGedService.ensureCreatedForDemandeCorrection(demande.getId());

        marche.setDemandeCorrection(demande);
        marche = marcheRepository.save(marche);
        demande.setMarche(marche);
        demandeCorrectionRepository.save(demande);

        seedCorrectionVisaDecision(demande, Role.DGD, "dgd");
        seedCorrectionVisaDecision(demande, Role.DGTCP, "dgtcp");
        seedCorrectionVisaDecision(demande, Role.DGI, "dgi");
        seedCorrectionVisaDecision(demande, Role.DGB, "dgb");
    }

    /**
     * Certificat {@value #DEMO_PRESIDENT_CI_NUMERO} lié à {@value #DEMO_CORRECTION_NUMERO} (ADOPTEE) :
     * statut {@link StatutCertificat#EN_VALIDATION_PRESIDENT}, visas DGD / DGTCP / DGI — test validation Président mise en place.
     */
    private void seedCertificatEnValidationPresidentDemo() {
        if (certificatCreditRepository.existsByNumero(DEMO_PRESIDENT_CI_NUMERO)) {
            return;
        }

        DemandeCorrection demandeAdoptee = demandeCorrectionRepository.findByNumero(DEMO_CORRECTION_NUMERO).orElse(null);
        if (demandeAdoptee == null || demandeAdoptee.getId() == null) {
            return;
        }
        Entreprise entreprise = entrepriseRepository.findByNif("NIF_DEFAULT").orElse(null);
        if (entreprise == null) {
            return;
        }

        BigDecimal valeurDouane = BigDecimal.valueOf(500_000);
        BigDecimal droitsHorsTva = BigDecimal.valueOf(120_000);
        BigDecimal tvaImport = BigDecimal.valueOf(80_000);
        BigDecimal marcheHt = BigDecimal.valueOf(600_000);
        BigDecimal tvaTravaux = BigDecimal.valueOf(200_000);

        CertificatCredit certificat = buildOpenCertificatEntity(
                DEMO_PRESIDENT_CI_NUMERO,
                demandeAdoptee,
                entreprise,
                valeurDouane,
                droitsHorsTva,
                tvaImport,
                marcheHt,
                tvaTravaux,
                droitsHorsTva,
                tvaTravaux.subtract(tvaImport));
        certificat.setStatut(StatutCertificat.EN_VALIDATION_PRESIDENT);
        certificat = certificatCreditRepository.save(certificat);

        seedCertificatVisaDecision(certificat, Role.DGD, "dgd");
        seedCertificatVisaDecision(certificat, Role.DGTCP, "dgtcp");
        seedCertificatVisaDecision(certificat, Role.DGI, "dgi");
    }

    /**
     * Certificat {@value #DEMO_CI_VISAS_DGD_DGI_NUMERO} lié à {@value #DEMO_CI_VISAS_DC_NUMERO} (ADOPTEE) :
     * statut {@link StatutCertificat#EN_CONTROLE}, visas DGD et DGI posés, montants DGTCP non renseignés
     * (saisie via {@code PATCH /api/certificats-credit/{id}/montants}).
     */
    private void seedCertificatEnControleVisasDgdDgiDemo() {
        var existing = certificatCreditRepository.findByNumero(DEMO_CI_VISAS_DGD_DGI_NUMERO);
        if (existing.isPresent()) {
            repairDemoCertificatSansMontantsDgtcp(existing.get());
            ensureCertificatVisasDgdDgiOnly(existing.get());
            return;
        }

        DemandeCorrection demandeAdoptee = ensureDemandeCorrectionAdopteeForCiVisasDemo();
        if (demandeAdoptee == null || demandeAdoptee.getId() == null) {
            return;
        }
        if (certificatCreditRepository.countByDemandeCorrectionIdAndStatutNot(demandeAdoptee.getId(), StatutCertificat.ANNULE) > 0) {
            return;
        }

        Entreprise entreprise = entrepriseRepository.findByNif("NIF_DEFAULT").orElse(null);
        if (entreprise == null) {
            return;
        }

        CertificatCredit certificat = CertificatCredit.builder()
                .numero(DEMO_CI_VISAS_DGD_DGI_NUMERO)
                .dateEmission(Instant.now())
                .dateValidite(Instant.now().plusSeconds(365L * 24 * 3600))
                .statut(StatutCertificat.EN_CONTROLE)
                .entreprise(entreprise)
                .demandeCorrection(demandeAdoptee)
                .build();
        certificat = certificatCreditRepository.save(certificat);

        seedCertificatVisaDecision(certificat, Role.DGD, "dgd");
        seedCertificatVisaDecision(certificat, Role.DGI, "dgi");
    }

    /** Remet à blanc les montants agrégés DGTCP sur le certificat démo (réparation seed). */
    private void repairDemoCertificatSansMontantsDgtcp(CertificatCredit certificat) {
        if (certificat == null) {
            return;
        }
        certificat.setMontantCordon(null);
        certificat.setMontantTVAInterieure(null);
        certificat.setSoldeCordon(null);
        certificat.setSoldeTVA(null);
        certificat.setValeurDouaneFournitures(null);
        certificat.setDroitsEtTaxesDouaneHorsTva(null);
        certificat.setTvaImportationDouaneAccordee(null);
        certificat.setTvaImportationDouane(null);
        certificat.setMontantMarcheHt(null);
        certificat.setTvaCollecteeTravaux(null);
        if (certificat.getStatut() != StatutCertificat.EN_CONTROLE) {
            certificat.setStatut(StatutCertificat.EN_CONTROLE);
        }
        certificatCreditRepository.save(certificat);
    }

    private void ensureCertificatVisasDgdDgiOnly(CertificatCredit certificat) {
        if (certificat == null || certificat.getId() == null) {
            return;
        }
        seedCertificatVisaDecision(certificat, Role.DGD, "dgd");
        seedCertificatVisaDecision(certificat, Role.DGI, "dgi");
    }

    private DemandeCorrection ensureDemandeCorrectionAdopteeForCiVisasDemo() {
        return demandeCorrectionRepository.findByNumero(DEMO_CI_VISAS_DC_NUMERO).orElseGet(() -> {
            AutoriteContractante autoriteContractante = autoriteContractanteRepository.findByCode("AC_DEFAULT")
                    .orElseThrow(() -> new IllegalStateException("Autorité Contractante par défaut manquante"));
            Entreprise entreprise = entrepriseRepository.findByNif("NIF_DEFAULT")
                    .orElseThrow(() -> new IllegalStateException("Entreprise par défaut manquante"));
            Convention convention = conventionRepository.findByReference("CONV-DEFAULT")
                    .orElseThrow(() -> new IllegalStateException("Convention par défaut manquante"));

            Marche marche = Marche.builder()
                    .numeroMarche(DEMO_CI_VISAS_MP_NUMERO)
                    .dateSignature(LocalDate.now())
                    .montantContratHt(BigDecimal.valueOf(720_000))
                    .statut(StatutMarche.EN_COURS)
                    .convention(convention)
                    .build();
            marche = marcheRepository.save(marche);

            DemandeCorrection demande = DemandeCorrection.builder()
                    .numero(DEMO_CI_VISAS_DC_NUMERO)
                    .dateDepot(Instant.now())
                    .statut(StatutDemande.ADOPTEE)
                    .autoriteContractante(autoriteContractante)
                    .entreprise(entreprise)
                    .convention(convention)
                    .build();

            applyCommissionVisaFlags(demande);

            ModeleFiscal modeleFiscal = ModeleFiscal.builder().demandeCorrection(demande).build();
            Dqe dqe = Dqe.builder().demandeCorrection(demande).build();
            demande.setModeleFiscal(modeleFiscal);
            demande.setDqe(dqe);
            demande = demandeCorrectionRepository.save(demande);

            dossierGedService.ensureCreatedForDemandeCorrection(demande.getId());

            marche.setDemandeCorrection(demande);
            marche = marcheRepository.save(marche);
            demande.setMarche(marche);
            return demandeCorrectionRepository.save(demande);
        });
    }

    private void applyCommissionVisaFlags(DemandeCorrection demande) {
        demande.setValidationDgd(true);
        demande.setValidationDgdDate(Instant.now());
        demande.setValidationDgdUserId(userId("dgd"));
        demande.setValidationDgtcp(true);
        demande.setValidationDgtcpDate(Instant.now());
        demande.setValidationDgtcpUserId(userId("dgtcp"));
        demande.setValidationDgi(true);
        demande.setValidationDgiDate(Instant.now());
        demande.setValidationDgiUserId(userId("dgi"));
        demande.setValidationDgb(true);
        demande.setValidationDgbDate(Instant.now());
        demande.setValidationDgbUserId(userId("dgb"));
    }

    private Long userId(String username) {
        return utilisateurRepository.findByUsername(username).map(Utilisateur::getId).orElse(null);
    }

    private Utilisateur requireSeedUser(String username) {
        return utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Utilisateur seed manquant: " + username));
    }

    private void seedCorrectionVisaDecision(DemandeCorrection demande, Role role, String username) {
        if (demande == null || demande.getId() == null) {
            return;
        }
        if (decisionCorrectionRepository.existsByDemandeCorrectionIdAndRoleAndDecision(
                demande.getId(), role, DecisionCorrectionType.VISA)) {
            return;
        }
        decisionCorrectionRepository.save(DecisionCorrection.builder()
                .demandeCorrection(demande)
                .role(role)
                .decision(DecisionCorrectionType.VISA)
                .dateDecision(Instant.now())
                .rejetTempStatus(RejetTempStatus.RESOLU)
                .rejetTempResolvedAt(Instant.now())
                .utilisateur(requireSeedUser(username))
                .build());
    }

    private void seedCertificatVisaDecision(CertificatCredit certificat, Role role, String username) {
        if (certificat == null || certificat.getId() == null) {
            return;
        }
        if (decisionCertificatCreditRepository.existsByCertificatCreditIdAndRoleAndDecision(
                certificat.getId(), role, DecisionCorrectionType.VISA)) {
            return;
        }
        decisionCertificatCreditRepository.save(DecisionCertificatCredit.builder()
                .certificatCredit(certificat)
                .role(role)
                .decision(DecisionCorrectionType.VISA)
                .dateDecision(Instant.now())
                .rejetTempStatus(RejetTempStatus.RESOLU)
                .rejetTempResolvedAt(Instant.now())
                .utilisateur(requireSeedUser(username))
                .build());
    }

    private static final String DEMO_CERTIFICAT_OUVERT_NUMERO = "CI-DEMO-OUVERT";

    /**
     * Certificat de crédit en statut {@link StatutCertificat#OUVERT} lié à la demande de démo {@link #DEMO_CORRECTION_NUMERO}.
     * Idempotent : ne crée rien si le numéro existe déjà ou si un certificat non annulé existe déjà pour cette demande.
     * <p>
     * Les jeux complets certificat + utilisations pour les tests d’intégration restent dans {@link TestWorkflowDataSeed} (profil {@code test}).
     */
    private void seedCertificatOuvertDemo() {
        if (certificatCreditRepository.existsByNumero(DEMO_CERTIFICAT_OUVERT_NUMERO)) {
            return;
        }
        DemandeCorrection demande = demandeCorrectionRepository.findByNumero(DEMO_CORRECTION_NUMERO).orElse(null);
        if (demande == null || demande.getId() == null) {
            return;
        }
        if (certificatCreditRepository.countByDemandeCorrectionIdAndStatutNot(demande.getId(), StatutCertificat.ANNULE) > 0) {
            return;
        }
        Entreprise entreprise = entrepriseRepository.findByNif("NIF_DEFAULT").orElse(null);
        if (entreprise == null) {
            return;
        }

        /*
         * Récapitulatif fiscal complet (lignes a–g) aligné sur docs/CERTIFICAT_RECAP_REFERENTIEL_METIER.md :
         * e = b + d (crédit extérieur), h = g − d (crédit intérieur), total e + h = 4_242_105.
         */
        BigDecimal valeurDouaneFournitures = BigDecimal.valueOf(9_746_681L);     // (a)
        BigDecimal droitsEtTaxesDouaneHorsTva = BigDecimal.valueOf(2_241_737L);  // (b)
        BigDecimal tvaImportationDouane = BigDecimal.valueOf(1_918_147L);       // (d)
        BigDecimal montantMarcheHt = BigDecimal.valueOf(12_502_300L);            // (f)
        BigDecimal tvaCollecteeTravaux = BigDecimal.valueOf(2_000_368L);         // (g)
        BigDecimal montantCordon = droitsEtTaxesDouaneHorsTva.add(tvaImportationDouane);           // (e)
        BigDecimal montantTVAInterieure = tvaCollecteeTravaux.subtract(tvaImportationDouane);     // (h)

        CertificatCredit certificat = CertificatCredit.builder()
                .numero(DEMO_CERTIFICAT_OUVERT_NUMERO)
                .dateEmission(Instant.now())
                .dateValidite(Instant.now().plusSeconds(365L * 24 * 3600))
                .valeurDouaneFournitures(valeurDouaneFournitures)
                .droitsEtTaxesDouaneHorsTva(droitsEtTaxesDouaneHorsTva)
                .tvaImportationDouaneAccordee(tvaImportationDouane)
                .tvaImportationDouane(tvaImportationDouane)
                .montantMarcheHt(montantMarcheHt)
                .tvaCollecteeTravaux(tvaCollecteeTravaux)
                .montantCordon(montantCordon)
                .montantTVAInterieure(montantTVAInterieure)
                .soldeCordon(droitsEtTaxesDouaneHorsTva)
                .soldeTVA(montantTVAInterieure)
                .statut(StatutCertificat.OUVERT)
                .entreprise(entreprise)
                .demandeCorrection(demande)
                .build();
        certificatCreditRepository.save(certificat);
    }

    /*
     * Jeux « petits montants » pour tester les utilisations de crédit à la main (sans créer d’utilisations ici).
     *
     * Entreprise : NIF_DEFAULT / user entreprise.
     *
     * Scénario A — CI-DEMO-SCEN-A (DC-DEMO-SCEN-A, marché MP-DEMO-SCEN-A)
     *   Récap : (a)=30, (b)=12, (d)=8  → (e)=20 = montantCordon = soldeCordon initial
     *          (f)=50, (g)=18, (d)=8    → (h)=10 = montantTVAInterieure = soldeTVA initial
     *   Piste de test : 3 à 5 utilisations DOUANIER (ex. liquidations 4+2, 3+1, 2+2 TVA… en restant ≤ cordon et ≤ restant (d)),
     *   puis utilisations TVA intérieure / apurement ; finir par clôture du certificat (sans transfert obligatoire).
     *
     * Scénario B — CI-DEMO-SCEN-B (DC-DEMO-SCEN-B, marché MP-DEMO-SCEN-B)
     *   Récap : (a)=20, (b)=10, (d)=10 → (e)=20 cordon ; (f)=30, (g)=16 → (h)=6 intérieur
     *   Piste de test : quelques importations (diminuent soldeCordon et tvaImportationDouane), puis demande de TRANSFERT
     *   (verse le restant (d) dans le stock TVA déductible, clôture les dossiers douaniers ouverts), puis clôture globale.
     *
     * Désactiver : app.seed.utilisation-credit-demo.enabled=false
     */
    private static final String DEMO_UC_SCEN_A_DC = "DC-DEMO-SCEN-A";
    private static final String DEMO_UC_SCEN_A_MP = "MP-DEMO-SCEN-A";
    private static final String DEMO_UC_SCEN_A_CI = "CI-DEMO-SCEN-A";
    private static final String DEMO_UC_SCEN_B_DC = "DC-DEMO-SCEN-B";
    private static final String DEMO_UC_SCEN_B_MP = "MP-DEMO-SCEN-B";
    private static final String DEMO_UC_SCEN_B_CI = "CI-DEMO-SCEN-B";
    private static final String DEMO_UC_SCEN_C_DC = "DC-DEMO-SCEN-C";
    private static final String DEMO_UC_SCEN_C_MP = "MP-DEMO-SCEN-C";
    private static final String DEMO_UC_SCEN_C_CI = "CI-DEMO-SCEN-C";
    private static final String DEMO_UC_SCEN_D_DC = "DC-DEMO-SCEN-D";
    private static final String DEMO_UC_SCEN_D_MP = "MP-DEMO-SCEN-D";
    private static final String DEMO_UC_SCEN_D_CI = "CI-DEMO-SCEN-D";
    private static final String DEMO_UC_SCEN_E_DC = "DC-DEMO-SCEN-E";
    private static final String DEMO_UC_SCEN_E_MP = "MP-DEMO-SCEN-E";
    private static final String DEMO_UC_SCEN_E_CI = "CI-DEMO-SCEN-E";
    private static final String DEMO_UC_SCEN_F_DC = "DC-DEMO-SCEN-F";
    private static final String DEMO_UC_SCEN_F_MP = "MP-DEMO-SCEN-F";
    private static final String DEMO_UC_SCEN_F_CI = "CI-DEMO-SCEN-F";

    private void seedUtilisationCreditDemoScenarios() {
        AutoriteContractante autoriteContractante;
        Entreprise entreprise;
        Convention convention;
        try {
            autoriteContractante = autoriteContractanteRepository.findByCode("AC_DEFAULT")
                    .orElseThrow(() -> new IllegalStateException("AC_DEFAULT manquante"));
            entreprise = entrepriseRepository.findByNif("NIF_DEFAULT")
                    .orElseThrow(() -> new IllegalStateException("Entreprise NIF_DEFAULT manquante"));
            convention = conventionRepository.findByReference("CONV-DEFAULT")
                    .orElseThrow(() -> new IllegalStateException("Convention CONV-DEFAULT manquante"));
        } catch (IllegalStateException e) {
            return;
        }

        DemandeCorrection dA = upsertAdopteeDemandeAvecMarche(DEMO_UC_SCEN_A_DC, DEMO_UC_SCEN_A_MP, autoriteContractante, entreprise, convention);
        DemandeCorrection dB = upsertAdopteeDemandeAvecMarche(DEMO_UC_SCEN_B_DC, DEMO_UC_SCEN_B_MP, autoriteContractante, entreprise, convention);
        DemandeCorrection dC = upsertAdopteeDemandeAvecMarche(DEMO_UC_SCEN_C_DC, DEMO_UC_SCEN_C_MP, autoriteContractante, entreprise, convention);
        DemandeCorrection dD = upsertAdopteeDemandeAvecMarche(DEMO_UC_SCEN_D_DC, DEMO_UC_SCEN_D_MP, autoriteContractante, entreprise, convention);
        DemandeCorrection dE = upsertAdopteeDemandeAvecMarche(DEMO_UC_SCEN_E_DC, DEMO_UC_SCEN_E_MP, autoriteContractante, entreprise, convention);
        DemandeCorrection dF = upsertAdopteeDemandeAvecMarche(DEMO_UC_SCEN_F_DC, DEMO_UC_SCEN_F_MP, autoriteContractante, entreprise, convention);

        if (dA != null && dA.getId() != null) {
            createPetitCertificatOuvertSiAbsent(DEMO_UC_SCEN_A_CI, dA, entreprise,
                    BigDecimal.valueOf(30), BigDecimal.valueOf(12), BigDecimal.valueOf(8),
                    BigDecimal.valueOf(50), BigDecimal.valueOf(18));
        }
        if (dB != null && dB.getId() != null) {
            createPetitCertificatOuvertSiAbsent(DEMO_UC_SCEN_B_CI, dB, entreprise,
                    BigDecimal.valueOf(20), BigDecimal.valueOf(10), BigDecimal.valueOf(10),
                    BigDecimal.valueOf(30), BigDecimal.valueOf(16));
        }
        if (dC != null && dC.getId() != null) {
            // Scénario C — solde entièrement épuisé → éligible à la clôture immédiatement
            // (a)=15, (b)=6, (d)=4 → (e)=10 ; (f)=25, (g)=8, (d)=4 → (h)=4
            createCertificatSoldeZeroSiAbsent(DEMO_UC_SCEN_C_CI, dC, entreprise,
                    BigDecimal.valueOf(15), BigDecimal.valueOf(6), BigDecimal.valueOf(4),
                    BigDecimal.valueOf(25), BigDecimal.valueOf(8));
        }
        if (dD != null && dD.getId() != null) {
            // Scénario D — certificat neuf, aucune utilisation créée, non éligible à la clôture
            // (a)=25, (b)=8, (d)=5 → (e)=13 ; (f)=40, (g)=14, (d)=5 → (h)=9
            createPetitCertificatOuvertSiAbsent(DEMO_UC_SCEN_D_CI, dD, entreprise,
                    BigDecimal.valueOf(25), BigDecimal.valueOf(8), BigDecimal.valueOf(5),
                    BigDecimal.valueOf(40), BigDecimal.valueOf(14));
        }
        if (dE != null && dE.getId() != null) {
            // Scénario E — solde cordon (e) = 100 MRU pour tests utilisation douanière
            // (a)=40, (b)=80, (d)=20 → (e)=100 ; (f)=50, (g)=22, (d)=20 → (h)=8
            createPetitCertificatOuvertSiAbsent(DEMO_UC_SCEN_E_CI, dE, entreprise,
                    BigDecimal.valueOf(40), BigDecimal.valueOf(80), BigDecimal.valueOf(20),
                    BigDecimal.valueOf(50), BigDecimal.valueOf(22));
        }
        if (dF != null && dF.getId() != null) {
            // Scénario F — solde cordon (e) = 200 MRU pour tests utilisation douanière
            // (a)=60, (b)=160, (d)=40 → (e)=200 ; (f)=80, (g)=55, (d)=40 → (h)=15 (g > d obligatoire)
            createPetitCertificatOuvertSiAbsent(DEMO_UC_SCEN_F_CI, dF, entreprise,
                    BigDecimal.valueOf(60), BigDecimal.valueOf(160), BigDecimal.valueOf(40),
                    BigDecimal.valueOf(80), BigDecimal.valueOf(55));
            repairDemoCertificatRecapSiCreditsNegatifs(DEMO_UC_SCEN_F_CI,
                    BigDecimal.valueOf(60), BigDecimal.valueOf(160), BigDecimal.valueOf(40),
                    BigDecimal.valueOf(80), BigDecimal.valueOf(55));
        }
    }

    /** Crée demande + marché ADOPTEE si le numéro de demande n’existe pas ; sinon retourne la demande existante. */
    private DemandeCorrection upsertAdopteeDemandeAvecMarche(
            String numeroDemande,
            String numeroMarche,
            AutoriteContractante autoriteContractante,
            Entreprise entreprise,
            Convention convention) {
        Optional<DemandeCorrection> existing = demandeCorrectionRepository.findByNumero(numeroDemande);
        if (existing.isPresent()) {
            return existing.get();
        }

        Marche marche = Marche.builder()
                .numeroMarche(numeroMarche)
                .dateSignature(LocalDate.now())
                .montantContratHt(BigDecimal.valueOf(100))
                .statut(StatutMarche.EN_COURS)
                .convention(convention)
                .build();
        marche = marcheRepository.save(marche);

        DemandeCorrection demande = DemandeCorrection.builder()
                .numero(numeroDemande)
                .dateDepot(Instant.now())
                .statut(StatutDemande.ADOPTEE)
                .autoriteContractante(autoriteContractante)
                .entreprise(entreprise)
                .convention(convention)
                .build();
        demande.setValidationDgd(true);
        demande.setValidationDgdDate(Instant.now());
        demande.setValidationDgdUserId(utilisateurRepository.findByUsername("dgd").map(Utilisateur::getId).orElse(null));
        demande.setValidationDgtcp(true);
        demande.setValidationDgtcpDate(Instant.now());
        demande.setValidationDgtcpUserId(utilisateurRepository.findByUsername("dgtcp").map(Utilisateur::getId).orElse(null));
        demande.setValidationDgi(true);
        demande.setValidationDgiDate(Instant.now());
        demande.setValidationDgiUserId(utilisateurRepository.findByUsername("dgi").map(Utilisateur::getId).orElse(null));
        demande.setValidationDgb(true);
        demande.setValidationDgbDate(Instant.now());
        demande.setValidationDgbUserId(utilisateurRepository.findByUsername("dgb").map(Utilisateur::getId).orElse(null));

        ModeleFiscal modeleFiscal = ModeleFiscal.builder().demandeCorrection(demande).build();
        Dqe dqe = Dqe.builder().demandeCorrection(demande).build();
        demande.setModeleFiscal(modeleFiscal);
        demande.setDqe(dqe);
        demande = demandeCorrectionRepository.save(demande);
        dossierGedService.ensureCreatedForDemandeCorrection(demande.getId());
        marche.setDemandeCorrection(demande);
        marche = marcheRepository.save(marche);
        demande.setMarche(marche);
        demandeCorrectionRepository.save(demande);
        return demande;
    }

    private void createPetitCertificatOuvertSiAbsent(
            String numeroCertificat,
            DemandeCorrection demande,
            Entreprise entreprise,
            BigDecimal valeurDouaneFournitures,
            BigDecimal droitsEtTaxesDouaneHorsTva,
            BigDecimal tvaImportationDouane,
            BigDecimal montantMarcheHt,
            BigDecimal tvaCollecteeTravaux) {
        if (certificatCreditRepository.existsByNumero(numeroCertificat)) {
            return;
        }
        if (demande == null || demande.getId() == null) {
            return;
        }
        if (certificatCreditRepository.countByDemandeCorrectionIdAndStatutNot(demande.getId(), StatutCertificat.ANNULE) > 0) {
            return;
        }
        applyRecapFiscalToCertificat(buildOpenCertificatEntity(
                numeroCertificat, demande, entreprise,
                valeurDouaneFournitures, droitsEtTaxesDouaneHorsTva, tvaImportationDouane,
                montantMarcheHt, tvaCollecteeTravaux,
                droitsEtTaxesDouaneHorsTva, tvaCollecteeTravaux.subtract(tvaImportationDouane)));
    }

    /**
     * Corrige un certificat démo déjà en base si le récapitulatif a été seedé avec (g) &lt; (d)
     * (crédit intérieur h = g − d négatif).
     */
    private void repairDemoCertificatRecapSiCreditsNegatifs(
            String numeroCertificat,
            BigDecimal valeurDouaneFournitures,
            BigDecimal droitsEtTaxesDouaneHorsTva,
            BigDecimal tvaImportationDouane,
            BigDecimal montantMarcheHt,
            BigDecimal tvaCollecteeTravaux) {
        certificatCreditRepository.findByNumero(numeroCertificat).ifPresent(existing -> {
            BigDecimal h = existing.getMontantTVAInterieure() != null
                    ? existing.getMontantTVAInterieure()
                    : BigDecimal.ZERO;
            BigDecimal soldeTva = existing.getSoldeTVA() != null ? existing.getSoldeTVA() : BigDecimal.ZERO;
            if (h.compareTo(BigDecimal.ZERO) >= 0 && soldeTva.compareTo(BigDecimal.ZERO) >= 0) {
                return;
            }
            existing.setValeurDouaneFournitures(valeurDouaneFournitures);
            existing.setDroitsEtTaxesDouaneHorsTva(droitsEtTaxesDouaneHorsTva);
            existing.setTvaImportationDouaneAccordee(tvaImportationDouane);
            if (existing.getTvaImportationDouane() == null
                    || existing.getTvaImportationDouane().compareTo(tvaImportationDouane) > 0) {
                existing.setTvaImportationDouane(tvaImportationDouane);
            }
            existing.setMontantMarcheHt(montantMarcheHt);
            existing.setTvaCollecteeTravaux(tvaCollecteeTravaux);
            BigDecimal montantCordon = droitsEtTaxesDouaneHorsTva.add(tvaImportationDouane);
            BigDecimal montantTVAInterieure = tvaCollecteeTravaux.subtract(tvaImportationDouane);
            existing.setMontantCordon(montantCordon);
            existing.setMontantTVAInterieure(montantTVAInterieure);
            if (existing.getSoldeCordon() == null || existing.getSoldeCordon().compareTo(BigDecimal.ZERO) < 0) {
                existing.setSoldeCordon(droitsEtTaxesDouaneHorsTva);
            }
            if (existing.getSoldeTVA() == null || existing.getSoldeTVA().compareTo(BigDecimal.ZERO) < 0) {
                existing.setSoldeTVA(montantTVAInterieure.max(BigDecimal.ZERO));
            }
            certificatCreditRepository.save(existing);
        });
    }

    private CertificatCredit buildOpenCertificatEntity(
            String numeroCertificat,
            DemandeCorrection demande,
            Entreprise entreprise,
            BigDecimal valeurDouaneFournitures,
            BigDecimal droitsEtTaxesDouaneHorsTva,
            BigDecimal tvaImportationDouane,
            BigDecimal montantMarcheHt,
            BigDecimal tvaCollecteeTravaux,
            BigDecimal soldeCordonInitial,
            BigDecimal soldeTvaInitial) {
        assertRecapCreditsNonNegatifs(droitsEtTaxesDouaneHorsTva, tvaImportationDouane, tvaCollecteeTravaux, numeroCertificat);
        BigDecimal montantCordon = droitsEtTaxesDouaneHorsTva.add(tvaImportationDouane);
        BigDecimal montantTVAInterieure = tvaCollecteeTravaux.subtract(tvaImportationDouane);
        return CertificatCredit.builder()
                .numero(numeroCertificat)
                .dateEmission(Instant.now())
                .dateValidite(Instant.now().plusSeconds(365L * 24 * 3600))
                .valeurDouaneFournitures(valeurDouaneFournitures)
                .droitsEtTaxesDouaneHorsTva(droitsEtTaxesDouaneHorsTva)
                .tvaImportationDouaneAccordee(tvaImportationDouane)
                .tvaImportationDouane(tvaImportationDouane)
                .montantMarcheHt(montantMarcheHt)
                .tvaCollecteeTravaux(tvaCollecteeTravaux)
                .montantCordon(montantCordon)
                .montantTVAInterieure(montantTVAInterieure)
                .soldeCordon(soldeCordonInitial)
                .soldeTVA(soldeTvaInitial.max(BigDecimal.ZERO))
                .statut(StatutCertificat.OUVERT)
                .entreprise(entreprise)
                .demandeCorrection(demande)
                .build();
    }

    private void applyRecapFiscalToCertificat(CertificatCredit certificat) {
        certificatCreditRepository.save(certificat);
    }

    private static void assertRecapCreditsNonNegatifs(
            BigDecimal droitsEtTaxesDouaneHorsTva,
            BigDecimal tvaImportationDouane,
            BigDecimal tvaCollecteeTravaux,
            String numeroCertificat) {
        if (droitsEtTaxesDouaneHorsTva.compareTo(BigDecimal.ZERO) < 0
                || tvaImportationDouane.compareTo(BigDecimal.ZERO) < 0
                || tvaCollecteeTravaux.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Récapitulatif invalide pour " + numeroCertificat + " : montants (b), (d) ou (g) négatifs");
        }
        BigDecimal h = tvaCollecteeTravaux.subtract(tvaImportationDouane);
        if (h.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Récapitulatif invalide pour " + numeroCertificat
                            + " : crédit intérieur (h) = (g)−(d) négatif ; augmenter (g) ou réduire (d)");
        }
    }

    /**
     * Crée un certificat OUVERT dont les soldes (cordon droits, TVA importation, TVA intérieure) sont
     * tous à zéro — simule un certificat entièrement consommé, éligible immédiatement à la clôture.
     */
    private void createCertificatSoldeZeroSiAbsent(
            String numeroCertificat,
            DemandeCorrection demande,
            Entreprise entreprise,
            BigDecimal valeurDouaneFournitures,
            BigDecimal droitsEtTaxesDouaneHorsTva,
            BigDecimal tvaImportationDouane,
            BigDecimal montantMarcheHt,
            BigDecimal tvaCollecteeTravaux) {
        if (certificatCreditRepository.existsByNumero(numeroCertificat)) {
            return;
        }
        if (demande == null || demande.getId() == null) {
            return;
        }
        if (certificatCreditRepository.countByDemandeCorrectionIdAndStatutNot(demande.getId(), StatutCertificat.ANNULE) > 0) {
            return;
        }
        BigDecimal montantCordon = droitsEtTaxesDouaneHorsTva.add(tvaImportationDouane);
        BigDecimal montantTVAInterieure = tvaCollecteeTravaux.subtract(tvaImportationDouane);

        CertificatCredit certificat = CertificatCredit.builder()
                .numero(numeroCertificat)
                .dateEmission(Instant.now())
                .dateValidite(Instant.now().plusSeconds(365L * 24 * 3600))
                .valeurDouaneFournitures(valeurDouaneFournitures)
                .droitsEtTaxesDouaneHorsTva(droitsEtTaxesDouaneHorsTva)
                .tvaImportationDouaneAccordee(tvaImportationDouane)
                .tvaImportationDouane(BigDecimal.ZERO)
                .montantMarcheHt(montantMarcheHt)
                .tvaCollecteeTravaux(tvaCollecteeTravaux)
                .montantCordon(montantCordon)
                .montantTVAInterieure(montantTVAInterieure)
                .soldeCordon(BigDecimal.ZERO)
                .soldeTVA(BigDecimal.ZERO)
                .statut(StatutCertificat.OUVERT)
                .entreprise(entreprise)
                .demandeCorrection(demande)
                .build();
        certificatCreditRepository.save(certificat);
    }

    /**
     * Seed du référentiel des taxes douanières courantes en Mauritanie.
     * Idempotent : utilise le code taxe comme clé d'unicité.
     */
    private void seedReferentielTaxes() {
        seedTaxe("DD",    "Droits de douane",                              null,    1);
        seedTaxe("TVA",   "TVA à l'importation",                           null,    2);
        seedTaxe("RS",    "Redevance statistique",                         null,    3);
        seedTaxe("PSC",   "Prélèvement de solidarité communautaire (PSC)", null,    4);
        seedTaxe("IMF",   "Impôt minimum forfaitaire (IMF)",               null,    5);
        seedTaxe("PC",    "Prélèvement communautaire (PC)",                null,    6);
        seedTaxe("TSI",   "Taxe sur les systèmes d'information (TSI)",     null,    7);
        seedTaxe("TIMBRE","Droit de timbre douanier",                      null,    8);
        seedTaxe("CAOM",  "Contribution aux activités opérationnelles MENA (CAOM)", null, 9);
        seedTaxe("PREF",  "Prélèvement redevance fiscale",                 null,    10);
    }

    private void seedTaxe(String code, String denomination, BigDecimal valeur, int ordre) {
        if (!referentielTaxeRepository.existsByCodeTaxe(code)) {
            referentielTaxeRepository.save(ReferentielTaxe.builder()
                    .codeTaxe(code)
                    .denominationTaxe(denomination)
                    .valeurTaxe(valeur)
                    .ordreAffichage(ordre)
                    .active(Boolean.TRUE)
                    .dateCreation(Instant.now())
                    .build());
        }
    }

    private void seedDefaultUsers() {
        AutoriteContractante autoriteContractante = createAutoriteContractanteIfMissing(
                "Ministère des Finances – Direction du Crédit d'impôt (autorité contractante pilote)",
                "AC_DEFAULT",
                "dcpi@finances.gov.mr | Nouakchott, Mauritanie"
        );
        Entreprise entreprise = createEntrepriseIfMissing(
                "Société Mauritanienne de Travaux Publics et Bâtiment (SMTPB)",
                "NIF_DEFAULT",
                "Zone industrielle, Tevragh-Zeina – Nouakchott",
                "REGULIERE"
        );
        Entreprise entreprise2 = createEntrepriseIfMissing(
                "Compagnie industrielle du Nord – Mauritanie (CIN-MR)",
                "NIF_TEST",
                "Nouadhibou, parc industriel – Mauritanie",
                "REGULIERE"
        );
        createConventionIfMissing(
                "CONV-DEFAULT",
                "Convention-cadre de financement – programme d'investissement public (échantillon SGCI)",
                "Banque Islamique de Développement (BID)",
                autoriteContractante);
        createUserIfMissing("admin", Role.ADMIN_SI, "Administrateur SGCI");
        createUserIfMissing("president", Role.PRESIDENT, "Président");
        createUserIfMissing("dgd", Role.DGD, "Agent DGD");
        createUserIfMissing("dgtcp", Role.DGTCP, "Agent DGTCP");
        createUserIfMissing("dgi", Role.DGI, "Agent DGI");
        createUserIfMissing("dgb", Role.DGB, "Agent DGB");
        createUserIfMissing("ac", Role.AUTORITE_CONTRACTANTE, "Autorité Contractante", autoriteContractante);
        createUserIfMissing("entreprise", Role.ENTREPRISE, "Entreprise", entreprise);
        createUserIfMissing("test", Role.ENTREPRISE, "Entreprise test", entreprise2);
        createUserIfMissing("commission_relais", Role.COMMISSION_RELAIS, "Support Commission (relais)");
    }

    private void createUserIfMissing(String username, Role role, String nomComplet) {
        createUserIfMissing(username, role, nomComplet, (AutoriteContractante) null, null);
    }

    private void createUserIfMissing(String username, Role role, String nomComplet, AutoriteContractante autoriteContractante) {
        createUserIfMissing(username, role, nomComplet, autoriteContractante, null);
    }

    private void createUserIfMissing(String username, Role role, String nomComplet, Entreprise entreprise) {
        createUserIfMissing(username, role, nomComplet, null, entreprise);
    }

    private void createUserIfMissing(String username, Role role, String nomComplet,
                                     AutoriteContractante autoriteContractante, Entreprise entreprise) {
        String email = seedUserEmail(username);
        Utilisateur existing = utilisateurRepository.findByUsername(username).orElse(null);
        if (existing == null) {
            Utilisateur user = Utilisateur.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode("123456"))
                    .role(role)
                    .autoriteContractante(autoriteContractante)
                    .entreprise(entreprise)
                    .nomComplet(nomComplet)
                    .email(email)
                    .actif(true)
                    .build();
            utilisateurRepository.save(user);
            return;
        }

        boolean changed = false;
        if (existing.getRole() != role) {
            existing.setRole(role);
            changed = true;
        }
        if (existing.getActif() == null || !existing.getActif()) {
            existing.setActif(true);
            changed = true;
        }
        if (existing.getNomComplet() == null || existing.getNomComplet().trim().isEmpty()) {
            existing.setNomComplet(nomComplet);
            changed = true;
        }
        if (existing.getEmail() == null || existing.getEmail().isBlank() || !email.equalsIgnoreCase(existing.getEmail())) {
            existing.setEmail(email);
            changed = true;
        }
        if (existing.getAutoriteContractante() == null && autoriteContractante != null) {
            existing.setAutoriteContractante(autoriteContractante);
            changed = true;
        }
        if (existing.getEntreprise() == null && entreprise != null) {
            existing.setEntreprise(entreprise);
            changed = true;
        }
        if (changed) {
            utilisateurRepository.save(existing);
        }
    }

    /** E-mail de test pour les comptes seed (ex. entreprise → entreprise@sharklasers.com). */
    private static String seedUserEmail(String username) {
        return username + "@sharklasers.com";
    }

    private Entreprise createEntrepriseIfMissing(String raisonSociale, String nif, String adresse, String situationFiscale) {
        return entrepriseRepository.findByNif(nif)
                .orElseGet(() -> entrepriseRepository.save(Entreprise.builder()
                        .raisonSociale(raisonSociale)
                        .nif(nif)
                        .adresse(adresse)
                        .situationFiscale(situationFiscale)
                        .build()));
    }

    private AutoriteContractante createAutoriteContractanteIfMissing(String nom, String code, String contact) {
        return autoriteContractanteRepository.findByCode(code)
                .orElseGet(() -> autoriteContractanteRepository.save(AutoriteContractante.builder()
                        .nom(nom)
                        .code(code)
                        .contact(contact)
                        .build()));
    }

    private void createConventionIfMissing(String reference, String intitule, String bailleurNom,
                                          AutoriteContractante autoriteContractante) {
        if (conventionRepository.findByReference(reference).isEmpty()) {
            Bailleur bailleur = createBailleurIfMissing(bailleurNom);
            conventionRepository.save(Convention.builder()
                    .reference(reference)
                    .intitule(intitule)
                    .bailleur(bailleur)
                    .autoriteContractante(autoriteContractante)
                    .statut(StatutConvention.EN_ATTENTE)
                    .build());
        }
    }

    private Bailleur createBailleurIfMissing(String nom) {
        return bailleurRepository.findByNom(nom)
                .orElseGet(() -> bailleurRepository.save(Bailleur.builder()
                        .nom(nom)
                        .build()));
    }

    private void seedPermissions() {
        createPermission("projet.create", "Créer un dossier projet");
        createPermission("projet.document.upload", "Déposer les documents projet");
        createPermission("projet.view", "Consulter ses projets");
        createPermission("projet.update", "Modifier les informations projet");
        createPermission("projet.validate", "Valider le référentiel projet");
        createPermission("projet.reject", "Rejeter le référentiel projet");
        createPermission("projet.view.all", "Consulter tous les référentiels");

        createPermission("convention.create", "Créer une convention");
        createPermission("convention.view", "Consulter ses conventions");
        createPermission("convention.view.all", "Consulter toutes les conventions");
        createPermission("convention.validate", "Valider une convention");
        createPermission("convention.reject", "Rejeter une convention");
        createPermission("convention.document.upload", "Déposer les documents convention");

        createPermission("correction.submit", "Soumettre une demande de correction fiscale");
        createPermission("correction.offer.upload", "Uploader l'offre fiscale");
        createPermission("correction.offer.view", "Visualiser l'offre fiscale");
        createPermission("correction.complement.add", "Déposer des pièces complémentaires");
        createPermission("correction.visa.history.view", "Consulter l'historique des visas");
        createPermission("correction.status.update", "Changer le statut d'une demande de correction");
        createPermission("correction.entreprise.queue.view", "Consulter ses demandes de correction");
        createPermission("correction.dgd.queue.view", "Consulter la file des dossiers à traiter");
        createPermission("correction.dgd.evaluate.nomenclature", "Évaluer la nomenclature douanière");
        createPermission("correction.dgd.evaluate.valeur", "Évaluer la valeur en douane");
        createPermission("correction.dgd.calculate", "Calculer la composante Douane/TVA");
        createPermission("correction.dgd.save", "Enregistrer l'évaluation");
        createPermission("correction.dgd.transmit", "Transmettre au visa Trésor");
        createPermission("correction.dgtcp.queue.view", "Consulter les dossiers visa Trésor");
        createPermission("correction.dgtcp.review", "Vérifier les calculs");
        createPermission("correction.dgtcp.finalize", "Arrêter le montant définitif");
        createPermission("correction.dgtcp.visa", "Apposer le visa Trésor");
        createPermission("correction.dgtcp.request_complements", "Demander des compléments");
        createPermission("correction.dgtcp.reject", "Rejeter la demande correction");
        createPermission("correction.dgi.queue.view", "Consulter les dossiers visa Impôts");
        createPermission("correction.dgi.visa", "Apposer le visa Impôts");
        createPermission("correction.dgi.reject", "Rejeter le visa Impôts");
        createPermission("correction.dgi.document.upload", "Téléverser le document crédit intérieur (visa DGI)");
        createPermission("correction.dgb.queue.view", "Consulter les dossiers visa Budget");
        createPermission("correction.dgb.visa", "Apposer le visa Budget");
        createPermission("correction.dgb.reject", "Rejeter le visa Budget");
        createPermission("correction.president.queue.view", "Consulter les dossiers à valider");
        createPermission("correction.president.history.view", "Visualiser l'historique complet");
        createPermission("correction.president.arbitrate", "Arbitrer un dossier");
        createPermission("correction.president.validate", "Valider la correction fiscale");
        createPermission("correction.president.letter.generate", "Éditer la lettre de correction");
        createPermission("correction.president.signature.upload", "Déposer le scan de signature");
        createPermission("correction.president.reject", "Rejeter la correction fiscale");
        createPermission("correction.view.audit", "Consulter correction (audit)");
        createPermission("correction.reclamation.submit", "Déposer une réclamation sur une correction adoptée ou notifiée");
        createPermission("correction.reclamation.annuler", "Annuler une réclamation en cours avant DGTCP (demande inchangée)");
        createPermission("correction.reclamation.traiter", "Accepter ou rejeter une réclamation sur une demande de correction");
        createPermission("correction.demande.reactivate", "Réactiver une demande de correction annulée (retour RECUE, AC)");
        createPermission("correction.demande.reactivate_rejetee", "Annuler un rejet définitif d'une demande de correction (retour EN_VALIDATION, ADMIN_SI)");
        createPermission("correction.admin_override", "Corriger un document ou une information d'une demande de correction, à tout moment (ADMIN_SI, motif obligatoire)");
        createPermission("certificat.admin_override", "Corriger un document ou une information d'un certificat de crédit, à tout moment (ADMIN_SI, motif obligatoire)");
        createPermission("utilisation.admin_override", "Corriger un document ou une information d'une demande d'utilisation, à tout moment (ADMIN_SI, motif obligatoire)");

        createPermission("mise_en_place.submit", "Soumettre une demande de mise en place");
        createPermission("mise_en_place.document.upload", "Déposer les pièces justificatives");
        createPermission("mise_en_place.view", "Consulter l'état d'avancement");
        createPermission("mise_en_place.annuler", "Annuler une demande de mise en place");
        createPermission("mise_en_place.entreprise.queue.view", "Consulter ses demandes de mise en place");
        createPermission("mise_en_place.dgd.queue.view", "Consulter les dossiers contrôle fiscal");
        createPermission("mise_en_place.dgd.validate", "Valider l'éligibilité");
        createPermission("mise_en_place.dgd.reject", "Rejeter la demande de mise en place");
        createPermission("mise_en_place.dgd.resolve", "Résoudre un rejet temporaire DGD");

        createPermission("mise_en_place.dgi.queue.view", "Consulter les dossiers contrôle fiscal");
        createPermission("mise_en_place.dgi.validate", "Valider l'éligibilité");
        createPermission("mise_en_place.dgi.reject", "Rejeter la demande de mise en place");
        createPermission("mise_en_place.dgi.resolve", "Résoudre un rejet temporaire DGI");

        createPermission("mise_en_place.dgtcp.queue.view", "Consulter les demandes de mise en place");
        createPermission("mise_en_place.dgtcp.validate", "Apposer le visa DGTCP");
        createPermission("mise_en_place.dgtcp.reject", "Rejeter temporairement (DGTCP)");
        createPermission("mise_en_place.dgtcp.open_credit", "Ouvrir le crédit d'impôt");
        createPermission("mise_en_place.dgtcp.allocate", "Ventiler le crédit");
        createPermission("mise_en_place.dgtcp.certificate.generate", "Générer le certificat");
        createPermission("mise_en_place.dgtcp.certificate.send", "Transmettre le certificat pour signature");
        createPermission("mise_en_place.dgtcp.resolve", "Résoudre un rejet temporaire DGTCP");

        createPermission("mise_en_place.dgb.queue.view", "Consulter les dossiers Budget");
        createPermission("mise_en_place.dgb.resolve", "Résoudre un rejet temporaire DGB");

        createPermission("mise_en_place.president.queue.view", "Consulter les certificats en attente");
        createPermission("mise_en_place.president.validate", "Valider le certificat");
        createPermission("mise_en_place.president.document.generate", "Déclencher l'édition du document officiel");
        createPermission("mise_en_place.president.signature.upload", "Déposer le scan signé");
        createPermission("mise_en_place.president.reject", "Rejeter le certificat");
        createPermission("mise_en_place.president.resolve", "Résoudre un rejet temporaire Président");

        createPermission("certificat.verification.scan",
                "Vérifier un certificat de crédit par scan du code-barres (numéro)");

        createPermission("utilisation.douane.submit", "Soumettre une demande d'utilisation Douane");
        createPermission("utilisation.douane.document.upload", "Déposer les pièces import");
        createPermission("utilisation.douane.solde.view", "Consulter le solde Douane");
        createPermission("utilisation.douane.history.view", "Consulter l'historique des imputations");
        createPermission("utilisation.douane.dgd.queue.view", "Consulter les demandes d'utilisation Douane");
        createPermission("utilisation.douane.dgd.verify", "Vérifier la comptabilité matière");
        createPermission("utilisation.douane.dgd.quittance.visa", "Viser le bulletin de liquidation");
        createPermission("utilisation.douane.dgd.reject", "Rejeter la demande Douane");
        createPermission("utilisation.douane.dgtcp.queue.view", "Consulter les demandes validées DGD");
        createPermission("utilisation.douane.dgtcp.impute", "Imputer les droits et taxes");
        createPermission("utilisation.douane.dgtcp.solde.update", "Mettre à jour le solde Douane");
        createPermission("utilisation.douane.entreprise.cheque", "Saisir le chèque certifié (après visa DGD)");
        createPermission("utilisation.douane.dgtcp.envoyer.tresor", "Envoyer la demande au Trésor");
        createPermission("utilisation.douane.dgtcp.quittances", "Saisir les quittances Trésor");
        createPermission("utilisation.douane.entreprise.reception", "Accuser réception du certificat d'utilisation");
        createPermission("utilisation.douane.dgtcp.history.view", "Consulter l'historique des liquidations");

        createPermission("utilisation.interieur.submit", "Soumettre une demande d'utilisation Intérieur");
        createPermission("utilisation.interieur.document.upload", "Déposer les justificatifs TVA");
        createPermission("utilisation.interieur.solde.view", "Consulter le solde Intérieur");
        createPermission("utilisation.interieur.history.view", "Consulter l'historique des apurements");
        createPermission("utilisation.interieur.dgtcp.queue.view", "Consulter les demandes d'utilisation Intérieur");
        createPermission("utilisation.interieur.dgtcp.verify", "Vérifier les justificatifs");
        createPermission("utilisation.interieur.dgtcp.validate", "Valider l'apurement");
        createPermission("utilisation.interieur.dgtcp.solde.update", "Mettre à jour le solde Intérieur");
        createPermission("utilisation.interieur.dgtcp.reject", "Rejeter la demande Intérieur");
        createPermission("utilisation.interieur.dgi.view", "Consulter les utilisations Intérieur");
        createPermission("utilisation.ac.view",
                "Consulter les utilisations de crédit liées aux certificats de son périmètre (AC / délégué)");
        createPermission("utilisation.interieur.dgi.decision", "Enregistrer visa ou rejet temporaire (DGI, TVA intérieure)");

        createPermission("utilisation.douane.dgd.resolve", "Résoudre un rejet temporaire (DGD, utilisation douane)");
        createPermission("utilisation.douane.dgtcp.resolve", "Résoudre un rejet temporaire (DGTCP, utilisation douane)");
        createPermission("utilisation.interieur.dgtcp.resolve", "Résoudre un rejet temporaire (DGTCP, TVA intérieure)");
        createPermission("utilisation.interieur.dgi.resolve", "Résoudre un rejet temporaire (DGI, TVA intérieure)");

        createPermission("utilisation.entreprise.rejet.repondre",
                "Répondre à un rejet temporaire sur une utilisation (message ou complément lié au dépôt de pièces)");

        createPermission("modification.submit", "Soumettre une demande de modification");
        createPermission("modification.document.upload", "Déposer les documents justificatifs");
        createPermission("modification.view", "Consulter le statut de modification");
        createPermission("modification.dgtcp.queue.view", "Consulter les demandes de modification");
        createPermission("modification.dgtcp.analyze", "Analyser l'impact sur les composantes");
        createPermission("modification.dgtcp.propose", "Proposer un ajustement des crédits");
        createPermission("modification.president.queue.view", "Consulter les propositions de modification");
        createPermission("modification.president.validate", "Valider la modification");
        createPermission("modification.president.reject", "Rejeter la modification");
        createPermission("modification.president.document.generate", "Déclencher l'édition du document");

        createPermission("transfert.submit", "Soumettre une demande de transfert de solde");
        createPermission("transfert.amount.set", "Indiquer le montant à transférer");
        createPermission("transfert.solde.view", "Consulter les soldes disponibles");
        createPermission("transfert.dgtcp.queue.view", "Consulter les demandes de transfert");
        createPermission("transfert.dgtcp.verify", "Vérifier la disponibilité du solde");
        createPermission("transfert.dgtcp.prepare", "Préparer l'opération de transfert");
        createPermission("transfert.dgtcp.update", "Mettre à jour les composantes");
        createPermission("transfert.president.validate", "Valider le transfert de solde");
        createPermission("transfert.president.reject", "Rejeter le transfert");
        createPermission("transfert.annuler", "Annuler une demande de transfert avant exécution par DGTCP / Président");
        createPermission("transfert.entreprise.rejet.repondre", "Répondre à un rejet temporaire sur une demande de transfert");

        createPermission("sous_traitance.submit", "Soumettre une demande de sous-traitance");
        createPermission("sous_traitance.solde.view", "Consulter ses demandes / autorisations de sous-traitance");
        createPermission("sous_traitance.dgtcp.queue.view", "Consulter les demandes de sous-traitance (DGTCP)");
        createPermission("sous_traitance.dgtcp.update", "Autoriser/refuser une sous-traitance (DGTCP)");

        createPermission("sous_traitant.list", "Lister les comptes sous-traitants");

        createPermission("cloture.queue.view", "Consulter les dossiers éligibles à clôture");
        createPermission("cloture.prepare", "Préparer la décision de clôture/annulation");
        createPermission("cloture.report.view", "Consulter les rapports et états statistiques");
        createPermission("cloture.report.generate", "Générer les rapports de suivi");
        createPermission("cloture.president.queue.view", "Consulter les propositions de clôture");
        createPermission("cloture.president.validate", "Valider la clôture/annulation");
        createPermission("cloture.president.reject", "Rejeter la clôture/annulation");

        createPermission("demande.explication.view", "Consulter les demandes d'explication (discussion commission)");
        createPermission("demande.explication.create", "Ouvrir une demande d'explication");
        createPermission("demande.explication.reply", "Répondre à une demande d'explication");
        createPermission("demande.explication.close", "Fermer une demande d'explication");
        createPermission("archivage.view", "Accéder aux archives complètes");

        createPermission("referentiel.taxe.manage", "Gérer le référentiel des taxes douanières (CRUD)");

        createPermission("user.create", "Créer un compte utilisateur");
        createPermission("user.update", "Modifier un compte utilisateur");
        createPermission("user.disable", "Désactiver ou réactiver un compte");
        createPermission("user.reset", "Réinitialiser un accès");
        createPermission("user.list", "Consulter la liste des utilisateurs");
        createPermission("user.role.assign", "Attribuer un rôle");
        createPermission("role.create", "Créer un rôle");
        createPermission("role.permissions.update", "Modifier les permissions d'un rôle");
        createPermission("role.list", "Consulter la liste des rôles");
        createPermission("role.disable", "Désactiver un rôle");
        createPermission("security.audit.view", "Consulter le journal d'activité");
        createPermission("security.logins.view", "Consulter le journal des connexions");
        createPermission("permissions.manage", "Gérer les permissions d'un rôle");
        createPermission("permissions.view", "Consulter les permissions");
        createPermission("document.requirements.view", "Consulter la configuration des documents requis");
        createPermission("document.types.view", "Consulter le référentiel des types de documents GED");
        createPermission("document.types.manage", "Gérer le référentiel et les exigences documentaires par processus");
        createPermission("entreprise.list", "Consulter la liste des entreprises");
        createPermission("entreprise.view.own", "Consulter sa propre entreprise");
        createPermission("entreprise.create", "Créer une entreprise");
        createPermission("entreprise.update", "Modifier une entreprise");
        createPermission("entreprise.delete", "Supprimer une entreprise");
        createPermission("groupement.list", "Consulter la liste des groupements");
        createPermission("groupement.create", "Créer un groupement");
        createPermission("groupement.update", "Modifier un groupement");
        createPermission("groupement.delete", "Supprimer un groupement");
        createPermission("marche.manage", "Gérer les marchés");
        createPermission("marche.view", "Consulter les marchés (lecture)");

        createPermission("bailleur.list", "Consulter la liste des bailleurs");
        createPermission("bailleur.create", "Créer un bailleur");
        createPermission("devise.list", "Consulter la liste des devises");
        createPermission("devise.create", "Créer une devise");
        createPermission("taux_change.view", "Consulter le taux de change");

        createPermission("delegue.list", "Consulter la liste des délégués");
        createPermission("delegue.create", "Créer un délégué");
        createPermission("delegue.update", "Modifier un délégué (identité, e-mail, mot de passe)");
        createPermission("delegue.disable", "Activer/désactiver un délégué");

        createPermission("reporting.view", "Consulter les tableaux de bord et statistiques agrégées");

        createPermission("commission.relais.list.entreprises", "Commission relais : lister les entreprises (choix d’impersonation)");
        createPermission("commission.relais.list.autorites", "Commission relais : lister les autorités contractantes");
        createPermission("commission.relais.impersonate.entreprise", "Commission relais : impersonation entreprise");
        createPermission("commission.relais.impersonate.autorite", "Commission relais : impersonation autorité contractante");
        createPermission("commission.relais.release", "Commission relais : quitter l’impersonation");
    }

    private void seedRolePermissions() {
        assign(Role.AUTORITE_CONTRACTANTE,
                "projet.create",
                "projet.document.upload",
                "projet.view",
                "projet.update",
                "convention.create",
                "convention.view",
                "convention.document.upload",
                "document.requirements.view",
                "entreprise.list",
                "entreprise.create",
                "entreprise.update",
                "groupement.list",
                "groupement.create",
                "groupement.update",
                "groupement.delete",
                "bailleur.list",
                "bailleur.create",
                "devise.list",
                "devise.create",
                "taux_change.view",
                "delegue.list",
                "delegue.create",
                "delegue.update",
                "delegue.disable",
                "correction.submit",
                "correction.offer.upload",
                "correction.offer.view",
                "correction.complement.add",
                "correction.visa.history.view",
                "correction.reclamation.submit",
                "correction.reclamation.annuler",
                "correction.demande.reactivate",
                "marche.manage",
                "mise_en_place.submit",
                "mise_en_place.document.upload",
                "mise_en_place.view",
                "modification.submit",
                "modification.document.upload",
                "modification.view",
                "utilisation.ac.view",
                "certificat.verification.scan",
                "reporting.view"
        );

        assign(Role.AUTORITE_UPM,
                "projet.create",
                "projet.document.upload",
                "projet.view",
                "projet.update",
                "convention.create",
                "convention.view",
                "convention.document.upload",
                "document.requirements.view",
                "entreprise.list",
                "entreprise.create",
                "entreprise.update",
                "groupement.list",
                "groupement.create",
                "groupement.update",
                "groupement.delete",
                "bailleur.list",
                "bailleur.create",
                "devise.list",
                "devise.create",
                "taux_change.view",
                "correction.submit",
                "correction.offer.upload",
                "correction.offer.view",
                "correction.complement.add",
                "correction.visa.history.view",
                "correction.reclamation.submit",
                "correction.reclamation.annuler",
                "marche.manage",
                "mise_en_place.submit",
                "mise_en_place.document.upload",
                "mise_en_place.view",
                "modification.submit",
                "modification.document.upload",
                "modification.view",
                "certificat.verification.scan",
                "reporting.view"
        );

        assign(Role.AUTORITE_UEP,
                "mise_en_place.annuler",
                "projet.create",
                "projet.document.upload",
                "projet.view",
                "projet.update",
                "convention.create",
                "convention.view",
                "convention.document.upload",
                "document.requirements.view",
                "entreprise.list",
                "entreprise.create",
                "entreprise.update",
                "groupement.list",
                "groupement.create",
                "groupement.update",
                "groupement.delete",
                "bailleur.list",
                "bailleur.create",
                "devise.list",
                "devise.create",
                "taux_change.view",
                "correction.submit",
                "correction.offer.upload",
                "correction.offer.view",
                "correction.complement.add",
                "correction.visa.history.view",
                "correction.reclamation.submit",
                "correction.reclamation.annuler",
                "marche.manage",
                "mise_en_place.submit",
                "mise_en_place.document.upload",
                "mise_en_place.view",
                "modification.submit",
                "modification.document.upload",
                "modification.view",
                "utilisation.ac.view",
                "certificat.verification.scan",
                "reporting.view"
        );

        assign(Role.ENTREPRISE,
                "mise_en_place.annuler",
                "correction.entreprise.queue.view",
                "correction.reclamation.submit",
                "correction.reclamation.annuler",
                "mise_en_place.entreprise.queue.view",
                "document.requirements.view",
                "entreprise.view.own",
                "utilisation.douane.submit",
                "utilisation.douane.document.upload",
                "utilisation.douane.solde.view",
                "utilisation.douane.history.view",
                "utilisation.douane.entreprise.cheque",
                "utilisation.douane.entreprise.reception",
                "utilisation.interieur.submit",
                "utilisation.interieur.document.upload",
                "utilisation.interieur.solde.view",
                "utilisation.interieur.history.view",
                "utilisation.entreprise.rejet.repondre",
                "modification.submit",
                "modification.document.upload",
                "modification.view",
                "transfert.submit",
                "transfert.amount.set",
                "transfert.solde.view",
                "transfert.annuler",
                "transfert.entreprise.rejet.repondre",
                "sous_traitance.submit",
                "sous_traitance.solde.view",
                "sous_traitant.list",
                "certificat.verification.scan",
                "reporting.view"
        );

        assign(Role.SOUS_TRAITANT,
                "document.requirements.view",
                "entreprise.view.own",
                "utilisation.douane.submit",
                "utilisation.douane.document.upload",
                "utilisation.douane.solde.view",
                "utilisation.douane.history.view",
                "utilisation.douane.entreprise.cheque",
                "utilisation.douane.entreprise.reception",
                "utilisation.interieur.submit",
                "utilisation.interieur.document.upload",
                "utilisation.interieur.solde.view",
                "utilisation.interieur.history.view",
                "utilisation.entreprise.rejet.repondre",
                "sous_traitance.submit",
                "sous_traitance.solde.view",
                "sous_traitant.list",
                "certificat.verification.scan",
                "reporting.view"
        );

        assign(Role.DGD,
                "document.requirements.view",
                "convention.view.all",
                "marche.view",
                "entreprise.list",
                "correction.dgd.queue.view",
                "correction.offer.view",
                "correction.offer.upload",
                "correction.visa.history.view",
                "correction.status.update",
                "correction.dgd.evaluate.nomenclature",
                "correction.dgd.evaluate.valeur",
                "correction.dgd.calculate",
                "correction.dgd.save",
                "correction.dgd.transmit",
                "utilisation.douane.dgd.queue.view",
                "utilisation.douane.dgd.verify",
                "utilisation.douane.dgd.quittance.visa",
                "utilisation.douane.dgd.reject",
                "utilisation.douane.dgd.resolve",
                "mise_en_place.dgd.queue.view",
                "mise_en_place.dgd.validate",
                "mise_en_place.dgd.reject",
                "mise_en_place.dgd.resolve",
                "reporting.view",
                "demande.explication.view",
                "demande.explication.create",
                "demande.explication.reply",
                "demande.explication.close",
                "certificat.verification.scan"
        );

        assign(Role.DGI,
                "document.requirements.view",
                "marche.view",
                "correction.dgi.queue.view",
                "correction.dgi.visa",
                "correction.dgi.reject",
                "correction.dgi.document.upload",
                "correction.status.update",
                "mise_en_place.dgi.queue.view",
                "mise_en_place.dgi.validate",
                "mise_en_place.dgi.reject",
                "mise_en_place.dgi.resolve",
                "utilisation.interieur.dgi.view",
                "utilisation.interieur.dgi.decision",
                "utilisation.interieur.dgi.resolve",
                "correction.offer.view",
                "convention.view.all",
                "convention.validate",
                "convention.reject",
                "correction.view.audit",
                "archivage.view",
                "user.create",
                "user.update",
                "user.disable",
                "user.reset",
                "user.list",
                "user.role.assign",
                "role.create",
                "role.permissions.update",
                "role.list",
                "role.disable",
                "security.audit.view",
                "security.logins.view",
                "permissions.manage",
                "permissions.view",
                "entreprise.list",
                "entreprise.create",
                "entreprise.update",
                "entreprise.delete",
                "groupement.list",
                "groupement.create",
                "groupement.update",
                "groupement.delete",
                "reporting.view",
                "demande.explication.view",
                "demande.explication.create",
                "demande.explication.reply",
                "demande.explication.close",
                "certificat.verification.scan"
        );

        assign(Role.DGB,
                "document.requirements.view",
                "convention.view.all",
                "convention.validate",
                "convention.reject",
                "marche.view",
                "entreprise.list",
                "projet.validate",
                "projet.reject",
                "projet.view",
                "correction.dgb.queue.view",
                "correction.dgb.visa",
                "correction.dgb.reject",
                "correction.offer.view",
                "reporting.view",
                "demande.explication.view",
                "demande.explication.create",
                "demande.explication.reply",
                "demande.explication.close",
                "certificat.verification.scan");

        // Président : accès complet (toutes les permissions enregistrées)
        assignAllPermissions(Role.PRESIDENT);

        assign(Role.DGTCP,
                "convention.view.all",
                "marche.view",
                "correction.dgtcp.queue.view",
                "correction.dgtcp.review",
                "correction.dgtcp.finalize",
                "correction.dgtcp.visa",
                "correction.dgtcp.request_complements",
                "correction.dgtcp.reject",
                "correction.offer.view",
                "correction.status.update",
                "correction.reclamation.traiter",
                "mise_en_place.dgtcp.queue.view",
                "mise_en_place.dgtcp.validate",
                "mise_en_place.dgtcp.reject",
                "mise_en_place.dgtcp.open_credit",
                "mise_en_place.dgtcp.allocate",
                "mise_en_place.dgtcp.certificate.generate",
                "mise_en_place.dgtcp.certificate.send",
                "mise_en_place.dgtcp.resolve",
                "mise_en_place.view",
                "document.requirements.view",
                "utilisation.douane.dgtcp.queue.view",
                "utilisation.douane.dgtcp.impute",
                "utilisation.douane.dgtcp.solde.update",
                "utilisation.douane.dgtcp.history.view",
                "utilisation.douane.dgtcp.resolve",
                "utilisation.douane.dgtcp.envoyer.tresor",
                "utilisation.douane.dgtcp.quittances",
                "utilisation.interieur.dgtcp.queue.view",
                "utilisation.interieur.dgtcp.verify",
                "utilisation.interieur.dgtcp.validate",
                "utilisation.interieur.dgtcp.solde.update",
                "utilisation.interieur.dgtcp.reject",
                "utilisation.interieur.dgtcp.resolve",
                "modification.dgtcp.queue.view",
                "modification.dgtcp.analyze",
                "modification.dgtcp.propose",
                "transfert.dgtcp.queue.view",
                "transfert.dgtcp.verify",
                "transfert.dgtcp.prepare",
                "transfert.dgtcp.update",
                "sous_traitance.dgtcp.queue.view",
                "sous_traitance.dgtcp.update",
                "entreprise.list",
                "reporting.view",
                "cloture.queue.view",
                "cloture.prepare",
                "cloture.report.view",
                "demande.explication.view",
                "demande.explication.create",
                "demande.explication.reply",
                "demande.explication.close",
                "certificat.verification.scan"
        );

        assign(Role.COMMISSION_RELAIS,
                "commission.relais.list.entreprises",
                "commission.relais.list.autorites",
                "commission.relais.impersonate.entreprise",
                "commission.relais.impersonate.autorite",
                "commission.relais.release",
                "document.requirements.view",
                "utilisation.douane.entreprise.cheque",
                "utilisation.douane.entreprise.reception",
                "referentiel.taxe.manage"
        );

    assign(Role.ADMIN_SI,
        "document.requirements.view",
        "document.types.view",
        "document.types.manage",
        "mise_en_place.annuler",
            "projet.view.all",
            "projet.validate",
            "projet.reject",
            "convention.view.all",
            "marche.view",
            "convention.validate",
            "convention.reject",
            "correction.view.audit",
            "correction.demande.reactivate_rejetee",
            "correction.admin_override",
            "certificat.admin_override",
            "utilisation.admin_override",
            "archivage.view",
            "referentiel.taxe.manage",
            "user.create",
            "user.update",
            "user.disable",
            "user.reset",
            "user.list",
            "user.role.assign",
            "role.create",
            "role.permissions.update",
            "role.list",
            "role.disable",
            "security.audit.view",
            "security.logins.view",
            "permissions.manage",
            "permissions.view",
            "entreprise.list",
            "entreprise.create",
            "entreprise.update",
            "entreprise.delete",
            "groupement.list",
            "groupement.create",
            "groupement.update",
            "groupement.delete",
            "reporting.view",
            "certificat.verification.scan"
    );

        seedReferentielReadPermissions();
        seedCertificatVerificationScanPermission();
    }

    /**
     * Référentiels métier (entreprise, convention, marché, projet) : lecture partagée pour tous les rôles.
     * Les autorités contractantes sont déjà lisibles via {@code GET /api/autorites-contractantes} (authentifié).
     */
    private void seedReferentielReadPermissions() {
        String[] readReferentiel = {
                "entreprise.list",
                "groupement.list",
                "convention.view.all",
                "marche.view",
                "projet.view.all"
        };
        for (Role role : Role.values()) {
            assign(role, readReferentiel);
        }
    }

    /** Scan / vérification code-barres certificat : tous les rôles (y compris bases déjà initialisées). */
    private void seedCertificatVerificationScanPermission() {
        for (Role role : Role.values()) {
            assign(role, "certificat.verification.scan");
        }
    }

private void createPermission(String code, String description) {
    if (!permissionRepository.existsByCode(code)) {
        permissionRepository.save(Permission.builder()
                .code(code)
                .description(description)
                .build());
    }
}

private void assign(Role role, String... permissionCodes) {
    for (String code : permissionCodes) {
        Permission permission = permissionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Permission manquante: " + code));
        rolePermissionRepository.findByRoleAndPermission(role, permission)
                .orElseGet(() -> rolePermissionRepository.save(RolePermission.builder()
                        .role(role)
                        .permission(permission)
                        .build()));
    }
}

private void assignAllPermissions(Role role) {
    for (Permission permission : permissionRepository.findAll()) {
        rolePermissionRepository.findByRoleAndPermission(role, permission)
                .orElseGet(() -> rolePermissionRepository.save(RolePermission.builder()
                        .role(role)
                        .permission(permission)
                        .build()));
    }
}
}