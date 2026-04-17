
package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Dqe;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.ModeleFiscal;
import mr.gov.finances.sgci.domain.entity.Permission;
import mr.gov.finances.sgci.domain.entity.RolePermission;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.PermissionRepository;
import mr.gov.finances.sgci.repository.RolePermissionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.service.DossierGedService;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.time.Instant;

@Component
@Order(100)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final AutoriteContractanteRepository autoriteContractanteRepository;
    private final ConventionRepository conventionRepository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final MarcheRepository marcheRepository;
    private final DocumentRequirementRepository documentRequirementRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final DossierGedService dossierGedService;
    private final Environment environment;

    @Override
    public void run(String... args) {
        if (utilisateurRepository.findByUsername("admin").isEmpty()) {
            Utilisateur admin = Utilisateur.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin"))
                    .role(Role.ADMIN_SI)
                    .nomComplet("Administrateur SGCI")
                    .actif(true)
                    .build();
            utilisateurRepository.save(admin);
        }

        seedPermissions();
        seedRolePermissions();
        seedDocumentRequirements();
        seedDefaultUsers();
        if (Boolean.TRUE.equals(environment.getProperty("app.seed.demande-correction.enabled", Boolean.class, Boolean.TRUE))) {
            seedDemandeCorrectionAdopteeDemo();
        }
    }

    private void seedDocumentRequirements() {
        EnumSet<TypeFichierAutorise> all = EnumSet.allOf(TypeFichierAutorise.class);

        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, TypeDocument.LETTRE_SAISINE, false,
                all, "Lettre de saisine", 1);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, TypeDocument.PV_OUVERTURE, false,
                all, "PV d’ouverture des offres financières", 2);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, TypeDocument.ATTESTATION_FISCALE, false,
                all, "Attestation fiscale de l’entreprise", 3);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, TypeDocument.OFFRE_FISCALE, false,
                all, "Offre fiscale", 4);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, TypeDocument.OFFRE_FINANCIERE, false,
                all, "Offre financière", 5);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, TypeDocument.TABLEAU_MODELE, false,
                all, "Tableau modèle (nature, valeur, classification)", 6);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, TypeDocument.DAO_DQE, false,
                all, "DAO + DQE", 7);
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, TypeDocument.LISTE_ITEMS, false,
                all, "Liste des items Excel (FR/AR)", 8);

        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, TypeDocument.FEUILLE_EVALUATION_SIGNEE, false,
                all, "Feuille d’évaluation signée", 9);

        /* GED / exigences pièces : ne pas retirer — utilisées par mise en place, utilisations, etc. */
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, TypeDocument.LETTRE_SAISINE, false,
                all, "Lettre de saisine", 1);
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, TypeDocument.CONTRAT, false,
                all, "Contrat enregistré", 2);
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, TypeDocument.LETTRE_NOTIFICATION_CONTRAT, false,
                all, "Lettre de notification du marché", 3);
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, TypeDocument.CERTIFICAT_NIF, false,
                all, "Certificat NIF", 4);
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, TypeDocument.LETTRE_CORRECTION, false,
                all, "Lettre de correction", 5);
        seedDocReq(ProcessusDocument.MISE_EN_PLACE_CI, TypeDocument.CERTIFICAT_CREDIT_IMPOTS, false,
                all, "Certificat de crédit d’impôt", 6);

        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, TypeDocument.DEMANDE_UTILISATION, false,
                all, "Demande d’utilisation", 1);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, TypeDocument.ORDRE_TRANSIT, false,
                all, "Ordre de transit", 2);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, TypeDocument.DECLARATION_DOUANE, false,
                all, "Déclaration en douane", 3);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, TypeDocument.BULLETIN_LIQUIDATION, false,
                all, "Bulletin de liquidation", 4);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, TypeDocument.FACTURE, false,
                all, "Facture commerciale", 5);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, TypeDocument.CONNAISSEMENT, false,
                all, "Connaissement / LTA / LVI", 6);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, TypeDocument.CERTIFICAT_CREDIT_IMPOTS_SYDONIA, false,
                all, "Copie du certificat (SYDONIA)", 7);

        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, TypeDocument.FACTURE, false,
                all, "Facture fournisseur", 1);
        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, TypeDocument.DECLARATION_TVA, false,
                all, "Déclaration TVA", 2);
        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, TypeDocument.DECOMPTE, false,
                all, "Décompte (selon cas)", 3);

        seedDocReq(ProcessusDocument.MODIFICATION_CI, TypeDocument.NOTE_SERVICE, false,
                all, "Note de service", 1);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, TypeDocument.JUSTIFICATIONS_LEGALES, false,
                all, "Justifications légales", 2);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, TypeDocument.LETTRES_MOTIVEES, false,
                all, "Lettres motivées", 3);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, TypeDocument.AVENANT_CONTRAT, false,
                all, "Avenant au contrat", 4);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, TypeDocument.LETTRES_AUTORITE_CONTRACTANTE, false,
                all, "Lettres de l’autorité contractante", 5);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, TypeDocument.DETAIL_CORRECTIONS_NECESSAIRES, false,
                all, "Détail des corrections nécessaires", 6);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, TypeDocument.DOCUMENTS_OFFICIELS, false,
                all, "Documents officiels", 7);
        seedDocReq(ProcessusDocument.MODIFICATION_CI, TypeDocument.DECISION_COMMISSION, false,
                all, "Décision de la commission / validation formelle", 8);

        seedDocReq(ProcessusDocument.TRANSFERT_CREDIT, TypeDocument.DEMANDE_MOTIVEE_TRANSFERT, true,
                all, "Demande motivée", 1);
        seedDocReq(ProcessusDocument.TRANSFERT_CREDIT, TypeDocument.DECLARATION_CLOTURE_DOUANE, true,
                all, "Déclaration de clôture", 2);
        seedDocReq(ProcessusDocument.TRANSFERT_CREDIT, TypeDocument.JUSTIFICATIFS_CLOTURE_DOUANE, true,
                all, "Justificatifs de clôture douane", 3);

        seedDocReq(ProcessusDocument.SOUS_TRAITANCE, TypeDocument.CONTRAT_SOUS_TRAITANCE_ENREGISTRE, false,
                all, "Contrat de sous-traitance enregistré", 1);
        seedDocReq(ProcessusDocument.SOUS_TRAITANCE, TypeDocument.LETTRE_SOUS_TRAITANCE, false,
                all, "Lettre détaillant volumes, quantités et pouvoirs", 2);

        seedDocReq(ProcessusDocument.CLOTURE_CI, TypeDocument.LISTE_CREDITS_A_CLOTURER, false,
                all, "Liste des crédits à annuler ou clôturer", 1);

        seedDocReq(ProcessusDocument.CONVENTION, TypeDocument.CONVENTION_JOIGNED_DOCUMENT, false,
                all, "Convention: document joint", 1);
        seedDocReq(ProcessusDocument.PROJET, TypeDocument.AUTRE_DOCUMENT, false,
                all, "Projet: document joint", 1);
        seedDocReq(ProcessusDocument.MARCHE, TypeDocument.AUTRE_DOCUMENT, false,
                all, "Marché: document joint", 1);

        upgradeTransfertCreditDocumentsToObligatoires();
    }

    /** Bases déjà seedées avec obligatoire=false : passage à obligatoire pour le P9. */
    private void upgradeTransfertCreditDocumentsToObligatoires() {
        for (TypeDocument type : java.util.List.of(
                TypeDocument.DEMANDE_MOTIVEE_TRANSFERT,
                TypeDocument.DECLARATION_CLOTURE_DOUANE,
                TypeDocument.JUSTIFICATIFS_CLOTURE_DOUANE)) {
            documentRequirementRepository.findByProcessusAndTypeDocument(ProcessusDocument.TRANSFERT_CREDIT, type)
                    .filter(req -> !Boolean.TRUE.equals(req.getObligatoire()))
                    .ifPresent(req -> {
                        req.setObligatoire(true);
                        documentRequirementRepository.save(req);
                    });
        }
    }

    private void seedDocReq(ProcessusDocument processus, TypeDocument type, boolean obligatoire,
                            EnumSet<TypeFichierAutorise> typesAutorises, String description, Integer ordre) {
        if (processus == null || type == null) {
            return;
        }
        if (documentRequirementRepository.existsByProcessusAndTypeDocument(processus, type)) {
            return;
        }
        DocumentRequirement req = DocumentRequirement.builder()
                .processus(processus)
                .typeDocument(type)
                .obligatoire(obligatoire)
                .typesAutorises(typesAutorises != null ? typesAutorises : EnumSet.noneOf(TypeFichierAutorise.class))
                .description(description)
                .ordreAffichage(ordre)
                .build();
        documentRequirementRepository.save(req);
    }

    private static final String DEMO_CORRECTION_NUMERO_PREFIX = "DC-DEFAULT-";
    private static final String DEMO_CORRECTION_NUMERO = "DC-DEFAULT-DEMO";
    private static final String DEMO_MARCHE_NUMERO = "MP-DEMO-DEFAULT-VOIRIE";

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
        Utilisateur existing = utilisateurRepository.findByUsername(username).orElse(null);
        if (existing == null) {
            Utilisateur user = Utilisateur.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode("123456"))
                    .role(role)
                    .autoriteContractante(autoriteContractante)
                    .entreprise(entreprise)
                    .nomComplet(nomComplet)
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

    private void createConventionIfMissing(String reference, String intitule, String bailleur,
                                          AutoriteContractante autoriteContractante) {
        if (conventionRepository.findByReference(reference).isEmpty()) {
            conventionRepository.save(Convention.builder()
                    .reference(reference)
                    .intitule(intitule)
                    .bailleur(bailleur)
                    .autoriteContractante(autoriteContractante)
                    .statut(StatutConvention.EN_ATTENTE)
                    .build());
        }
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
        createPermission("archivage.view", "Accéder aux archives complètes");

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
        createPermission("entreprise.list", "Consulter la liste des entreprises");
        createPermission("entreprise.create", "Créer une entreprise");
        createPermission("entreprise.update", "Modifier une entreprise");
        createPermission("entreprise.delete", "Supprimer une entreprise");
        createPermission("marche.manage", "Gérer les marchés");

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
                "reporting.view"
        );

        assign(Role.ENTREPRISE,
                "mise_en_place.annuler",
                "correction.entreprise.queue.view",
                "correction.reclamation.submit",
                "correction.reclamation.annuler",
                "mise_en_place.entreprise.queue.view",
                "document.requirements.view",
                "utilisation.douane.submit",
                "utilisation.douane.document.upload",
                "utilisation.douane.solde.view",
                "utilisation.douane.history.view",
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
                "sous_traitance.submit",
                "sous_traitance.solde.view",
                "sous_traitant.list",
                "reporting.view"
        );

        assign(Role.SOUS_TRAITANT,
                "document.requirements.view",
                "utilisation.douane.submit",
                "utilisation.douane.document.upload",
                "utilisation.douane.solde.view",
                "utilisation.douane.history.view",
                "utilisation.interieur.submit",
                "utilisation.interieur.document.upload",
                "utilisation.interieur.solde.view",
                "utilisation.interieur.history.view",
                "utilisation.entreprise.rejet.repondre",
                "sous_traitance.submit",
                "sous_traitance.solde.view",
                "sous_traitant.list",
                "reporting.view"
        );

        assign(Role.DGD,
                "document.requirements.view",
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
                "reporting.view"
        );

        assign(Role.DGI,
                "document.requirements.view",
                "correction.dgi.queue.view",
                "correction.dgi.visa",
                "correction.dgi.reject",
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
                "reporting.view"
        );

        assign(Role.DGB,
                "document.requirements.view",
                "convention.view.all",
                "convention.validate",
                "convention.reject",
                "projet.validate",
                "projet.reject",
                "projet.view",
                "correction.dgb.queue.view",
                "correction.dgb.visa",
                "correction.dgb.reject",
                "correction.offer.view",
                "reporting.view");

        // Président : accès complet (toutes les permissions enregistrées)
        assignAllPermissions(Role.PRESIDENT);

        assign(Role.DGTCP,
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
                "reporting.view"
        );

    assign(Role.ADMIN_SI,
        "document.requirements.view",
        "mise_en_place.annuler",
            "projet.view.all",
            "projet.validate",
            "projet.reject",
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
            "reporting.view"
    );
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