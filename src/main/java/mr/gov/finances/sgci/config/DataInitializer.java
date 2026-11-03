
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
import mr.gov.finances.sgci.domain.entity.ParametreApplication;
import mr.gov.finances.sgci.service.ParametreService;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.SensDocument;
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
import mr.gov.finances.sgci.repository.ParametreApplicationRepository;
import mr.gov.finances.sgci.repository.PermissionRepository;
import mr.gov.finances.sgci.repository.ReferentielTaxeRepository;
import mr.gov.finances.sgci.repository.RolePermissionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.service.DossierGedService;
import mr.gov.finances.sgci.service.ReferentielTypeDocumentService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

@Component
@Order(100)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

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
    private final ParametreApplicationRepository parametreApplicationRepository;
    private final DossierGedService dossierGedService;
    private final ReferentielTypeDocumentService referentielTypeDocumentService;
    private final mr.gov.finances.sgci.service.SecteurActiviteService secteurActiviteService;
    private final DocumentRequirementLegacyMigration documentRequirementLegacyMigration;
    private final EntrepriseLegacyGroupementMigration entrepriseLegacyGroupementMigration;
    private final ReferenceBackfillMigration referenceBackfillMigration;
    private final Environment environment;
    private final mr.gov.finances.sgci.config.seed.SeedRbac seedRbac;
    private final mr.gov.finances.sgci.config.seed.SeedDocumentRequirements seedDocumentRequirements;

    @Override
    public void run(String... args) {
        if (utilisateurRepository.findByUsername("admin").isEmpty()) {
            Utilisateur admin = Utilisateur.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode(motDePasseAdministrateurInitial(environment)))
                    .role(Role.ADMIN_SI)
                    .nomComplet("Administrateur SGCI")
                    .email(seedUserEmail("admin"))
                    .actif(true)
                    .build();
            utilisateurRepository.save(admin);
        }

        seedRbac.seed();
        referentielTypeDocumentService.seedMissingFromEnum();
        secteurActiviteService.seedInitial();
        documentRequirementLegacyMigration.migrateIfNeeded();
        // Avant tout seed / création d'entreprise : retire les colonnes NOT NULL obsolètes.
        entrepriseLegacyGroupementMigration.migrateIfNeeded();
        seedDocumentRequirements.seed();
        seedParametresApplication();
        if (comptesDeDemonstration(environment)) {
            seedDefaultUsers();
        }
        seedReferentielTaxes();
        if (donneesDeDemonstration(environment, "app.seed.demande-correction.enabled")) {
            seedDemo("demande de correction adoptée", this::seedDemandeCorrectionAdopteeDemo);
            seedDemo("demande en validation Président", this::seedDemandeCorrectionEnValidationPresidentDemo);
            seedDemo("certificat en validation Président", this::seedCertificatEnValidationPresidentDemo);
            seedDemo("certificat en contrôle, visas DGD et DGI", this::seedCertificatEnControleVisasDgdDgiDemo);
        }
        if (donneesDeDemonstration(environment, "app.seed.utilisation-credit-demo.enabled")
                && donneesDeDemonstration(environment, "app.seed.demande-correction.enabled")) {
            seedDemo("scénarios d'utilisation du crédit", this::seedUtilisationCreditDemoScenarios);
        }
        if (Boolean.TRUE.equals(environment.getProperty("app.seed.certificat-ouvert.enabled", Boolean.class, Boolean.FALSE))
                && !ExigencesProduction.estProduction(environment)) {
            seedDemo("certificat ouvert", this::seedCertificatOuvertDemo);
        }
        referenceBackfillMigration.migrateIfNeeded();
        signalerComptesAuMotDePasseConnu();
    }

    /** Les comptes que les versions précédentes créaient en production, et leurs mots de passe. */
    private static final List<String> COMPTES_DE_DEMONSTRATION = List.of(
            "admin", "president", "dgd", "dgtcp", "dgi", "dgb", "ac", "entreprise", "test", "commission_relais");

    /**
     * Comptes et données de démonstration : actifs hors production, jamais en production sauf
     * demande explicite. Les versions précédentes créaient en production dix comptes au mot de passe
     * {@code 123456}, et chaque démarrage réactivait ceux qu'un administrateur avait désactivés.
     */
    static boolean comptesDeDemonstration(Environment env) {
        return env.getProperty("app.seed.comptes-demo.enabled", Boolean.class, !ExigencesProduction.estProduction(env));
    }

    static boolean donneesDeDemonstration(Environment env, String cle) {
        return env.getProperty(cle, Boolean.class, !ExigencesProduction.estProduction(env));
    }

    /**
     * Le mot de passe du compte {@code admin}, quand il n'existe pas encore. Hors production, celui
     * du poste de développement ; en production, {@code APP_ADMIN_INITIAL_PASSWORD}, à changer
     * après la première connexion. Sans lui, le démarrage s'arrête plutôt que de créer un compte
     * administrateur au mot de passe connu.
     */
    static String motDePasseAdministrateurInitial(Environment env) {
        if (!ExigencesProduction.estProduction(env)) {
            return "admin";
        }
        String mdp = env.getProperty("app.admin.initial-password", "");
        if (ExigencesProduction.inutilisable(mdp) || mdp.length() < 12) {
            throw new IllegalStateException("Compte administrateur absent : définir APP_ADMIN_INITIAL_PASSWORD "
                    + "(12 caractères au moins) pour le créer, puis changer ce mot de passe après la première connexion");
        }
        return mdp;
    }

    /**
     * En production, nomme dans le journal les comptes de démonstration encore actifs avec leur mot
     * de passe d'origine. Ils ont été créés par les versions précédentes : les désactiver ou changer
     * leur mot de passe est une décision d'exploitation, pas du démarrage.
     */
    private void signalerComptesAuMotDePasseConnu() {
        if (!ExigencesProduction.estProduction(environment)) {
            return;
        }
        List<String> exposes = new ArrayList<>();
        for (String username : COMPTES_DE_DEMONSTRATION) {
            utilisateurRepository.findByUsername(username)
                    .filter(u -> !Boolean.FALSE.equals(u.getActif()) && u.getPasswordHash() != null)
                    .filter(u -> passwordEncoder.matches("123456", u.getPasswordHash())
                            || passwordEncoder.matches("admin", u.getPasswordHash()))
                    .ifPresent(u -> exposes.add(username));
        }
        if (!exposes.isEmpty()) {
            log.error("SÉCURITÉ : comptes actifs au mot de passe connu de tous, à désactiver ou à changer : {}", exposes);
        }
    }

    /**
     * Crée un jeu de données de démonstration sans jamais bloquer le démarrage.
     *
     * <p>Ces données ne servent qu'aux essais. Le 2026-10-27, une seule d'entre elles — un doublon
     * refusé par MySQL — arrêtait l'application, que Docker relançait aussitôt : plus de deux mille
     * redémarrages, et des 502 pour tous les utilisateurs. Un échec est désormais journalisé, et le
     * démarrage continue sans cette donnée. Les données de référence (permissions, pièces, utilisateurs)
     * ne passent pas par ici : sans elles l'application ne fonctionne pas, et mieux vaut qu'elle
     * refuse de démarrer.
     */
    static void seedDemo(String nom, Runnable seed) {
        try {
            seed.run();
        } catch (RuntimeException e) {
            log.error("Données de démonstration « {} » non créées ; le démarrage continue sans elles.", nom, e);
        }
    }

    /**
     * Les coordonnées de contact du pied de page, jusqu'ici écrites en dur dans l'écran.
     *
     * <p>Semées avec les valeurs en vigueur, pour que la mise en service ne change rien à
     * l'affichage. Idempotent comme le reste : une valeur modifiée depuis l'écran de paramétrage
     * n'est jamais réécrite au redémarrage — sans quoi le paramétrage ne servirait à rien.
     */
    private void seedParametresApplication() {
        seedParametre(ParametreService.CONTACT_ADRESSE, "Nouakchott, Mauritanie",
                "Adresse affichée dans le pied de page");
        seedParametre(ParametreService.CONTACT_EMAIL, "contact@commission-fiscale.gov.mr",
                "Adresse de courriel de contact");
        seedParametre(ParametreService.CONTACT_TELEPHONE, "+222 48 69 89 83",
                "Numéro de téléphone de contact");
        seedParametre(ParametreService.DIGITALISATION_DATE_BASCULE, "",
                "Date de mise en service de la plateforme (AAAA-MM-JJ), frontière avant / après des indicateurs d'impact");
    }

    private void seedParametre(String cle, String valeur, String description) {
        if (parametreApplicationRepository.existsByCle(cle)) {
            return;
        }
        parametreApplicationRepository.save(ParametreApplication.builder()
                .cle(cle)
                .valeur(valeur)
                .description(description)
                .dateModification(Instant.now())
                .build());
    }

    /**
     * Classe en sortie les pièces que la Commission produit, sans jamais écraser un paramétrage.
     *
     * <p>La liste est reprise des trois classements qui existaient déjà en dur — les pièces
     * « produites par la Commission » du formulaire de dépôt, les pièces « d'administration »
     * masquées aux utilisations, et l'étagement de la GED. Rien n'y est inventé.
     *
     * <p><b>N'écrit que si le sens est nul</b>, c'est-à-dire si personne n'a tranché. Un
     * administrateur qui classe délibérément une pièce en entrée ne doit pas voir sa décision
     * reprise au redémarrage suivant. Le seed ne force aucune valeur déjà en base : les pièces du
     * transfert, repassées en obligatoire à chaque démarrage, défaisaient le choix fait dans la GED.
     *
     * <p>{@code RECU_DEPOT} reste volontairement en entrée : c'est la preuve de dépôt du déposant,
     * elle lui est due immédiatement. La classer en sortie la lui masquerait jusqu'à l'adoption.
     */




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
        // Le numéro est unique dans toute la base : c'est lui qu'il faut tester d'abord. Ne chercher la
        // démo que sous l'entreprise NIF_DEFAULT la recréait dès qu'elle n'y était plus rattachée — un
        // doublon que MySQL refuse, et qui a bloqué le démarrage en production le 2026-10-27.
        if (demandeCorrectionRepository.existsByNumero(DEMO_CORRECTION_NUMERO)) {
            return;
        }
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
            seedVisasCommission(demande);

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

    /**
     * Une demande de démonstration créée directement adoptée doit porter les quatre visas comme une
     * demande instruite : sans eux, sa fiche montrait la DGD « en attente de décision » (anomalie A11).
     */
    private void seedVisasCommission(DemandeCorrection demande) {
        seedCorrectionVisaDecision(demande, Role.DGD, "dgd");
        seedCorrectionVisaDecision(demande, Role.DGTCP, "dgtcp");
        seedCorrectionVisaDecision(demande, Role.DGI, "dgi");
        seedCorrectionVisaDecision(demande, Role.DGB, "dgb");
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
         * e = b + c + d (crédit extérieur, ici sans taxes de consommation), h = g − d (crédit intérieur),
         * total e + h = 4_242_105.
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
        seedVisasCommission(demande);
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
        createUserIfMissing("dgd", Role.DGD, "Représentant DGD");
        createUserIfMissing("dgtcp", Role.DGTCP, "Représentant DGTCP");
        createUserIfMissing("dgi", Role.DGI, "Représentant DGI");
        createUserIfMissing("dgb", Role.DGB, "Représentant DGB");
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



    /**
     * Retire une permission devenue sans objet, et ses attributions.
     *
     * <p>{@link #assign} ne fait qu'ajouter : retirer un code d'une liste ne le retire pas des bases
     * déjà initialisées, où il continuerait de figurer dans les jetons. Seul cas à ce jour :
     * {@code utilisation.douane.entreprise.reception}, l'accusé de réception par lequel l'entreprise
     * clôturait l'utilisation — la clôture revient désormais à la DGTCP.
     */

    /**
     * Référentiels métier (entreprise, convention, marché, projet) : lecture partagée pour tous les rôles.
     * Les autorités contractantes sont déjà lisibles via {@code GET /api/autorites-contractantes} (authentifié).
     *
     * <p>Sauf les conventions et les projets pour l'entreprise et le sous-traitant : ils ne lisent
     * que ceux de leurs propres dossiers ({@code convention.view} / {@code projet.view}, périmètre
     * appliqué par le service). Décision de la Commission sur l'anomalie A30 ; le retrait sur les
     * bases existantes est fait par {@code PermissionsRevoqueesMigration}.
     */

    /** Scan / vérification code-barres certificat : tous les rôles (y compris bases déjà initialisées). */



/**
 * Toutes les permissions, sauf les suppléances de l'administrateur ({@code *admin_override}) : les
 * services les réservent à l'ADMIN_SI, et le Président voyait des boutons « à la place de » qui
 * aboutissaient en 403 (anomalie A45).
 */
}