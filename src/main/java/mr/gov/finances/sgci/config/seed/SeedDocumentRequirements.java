package mr.gov.finances.sgci.config.seed;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.SensDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class SeedDocumentRequirements {

    private final DocumentRequirementRepository documentRequirementRepository;

    public void seed() {
        seedDocumentRequirements();
        marquerPiecesDeSortie();
    }

    private void marquerPiecesDeSortie() {
        marquerSorties(ProcessusDocument.CORRECTION_OFFRE_FISCALE,
                "OFFRE_FISCALE_CORRIGEE", "OFFRE_CORRIGEE", "CREDIT_INTERIEUR", "CREDIT_EXTERIEUR",
                "LETTRE_ADOPTION", "FEUILLE_EVALUATION_SIGNEE");
        marquerSorties(ProcessusDocument.MISE_EN_PLACE_CI,
                "CERTIFICAT_CREDIT_IMPOTS");
        marquerSorties(ProcessusDocument.UTILISATION_CI_DOUANE,
                "BULLETIN_ANNOTE", "QUITTANCE_TRESOR", "CERTIFICAT_UTILISATION");
        marquerSorties(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE,
                "QUITTANCE_DGI", "CERTIFICAT_UTILISATION");
        marquerSorties(ProcessusDocument.MODIFICATION_CI,
                "DECISION_COMMISSION");
    }
    private void marquerSorties(ProcessusDocument processus, String... codes) {
        for (String code : codes) {
            documentRequirementRepository.findByProcessusAndCodeDocument(processus, code)
                    .filter(req -> req.getSens() == null)
                    .ifPresent(req -> {
                        req.setSens(SensDocument.SORTIE);
                        documentRequirementRepository.save(req);
                    });
        }
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
        // Produit par le système à la soumission, jamais réclamé au déposant : obligatoire = false.
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "RECU_DEPOT", false,
                all, "Reçu de dépôt de la demande", 15);
        // Facultative au référentiel, qui ne sait exprimer qu'une obligation inconditionnelle :
        // elle ne devient obligatoire que si la case « équipements » est cochée (DemandeCorrectionService).
        seedDocReq(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "LISTE_EQUIPEMENTS", false,
                all, "Liste des équipements et véhicules sous régime", 16);

        /* Rattachement d'une personne à une entité : justificatifs déposés sans compte.
         * Le mandat est la seule pièce obligatoire — c'est elle qui établit que l'entité habilite
         * le demandeur. Les deux autres éclairent la décision sans la conditionner, et l'ADMIN_SI
         * peut réviser ce paramétrage sans livraison. */
        seedDocReq(ProcessusDocument.RATTACHEMENT_COMPTE, "MANDAT_REPRESENTATION", true,
                all, "Mandat habilitant le demandeur à représenter l'entité", 1);
        seedDocReq(ProcessusDocument.RATTACHEMENT_COMPTE, "PIECE_IDENTITE", false,
                all, "Pièce d'identité du demandeur", 2);
        seedDocReq(ProcessusDocument.RATTACHEMENT_COMPTE, "REGISTRE_COMMERCE", false,
                all, "Registre du commerce (rattachement à une entreprise)", 3);

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
        // obligatoire = false impérativement : assertRequiredDocumentsPresent est appelé à l'ENTRÉE
        // du circuit (EN_VERIFICATION), alors que cette pièce n'existe qu'à la sortie.
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "CERTIFICAT_UTILISATION", false,
                EnumSet.of(TypeFichierAutorise.PDF, TypeFichierAutorise.IMAGE),
                "Certificat d'utilisation signé et cacheté par le Président", 8);
        // Les pièces produites par l'administration. Elles étaient écrites directement en base par
        // UtilisationCreditService, donc absentes du référentiel : impossibles à déposer par la
        // route normale, et impossibles à réclamer dans un rejet temporaire. Même raison que
        // ci-dessus pour obligatoire = false — elles n'existent qu'en cours ou en fin de circuit,
        // alors que le contrôle de complétude s'exerce à l'entrée.
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "BULLETIN_ANNOTE", false,
                EnumSet.of(TypeFichierAutorise.PDF, TypeFichierAutorise.IMAGE),
                "Bulletin de liquidation annoté par la DGD", 9);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "CHEQUE_CERTIFIE", false,
                EnumSet.of(TypeFichierAutorise.PDF, TypeFichierAutorise.IMAGE),
                "Chèque certifié couvrant la part à payer", 10);
        seedDocReq(ProcessusDocument.UTILISATION_CI_DOUANE, "QUITTANCE_TRESOR", false,
                EnumSet.of(TypeFichierAutorise.PDF, TypeFichierAutorise.IMAGE),
                "Quittance du Trésor", 11);

        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, "FACTURE", false,
                all, "Facture fournisseur", 1);
        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, "DECLARATION_TVA", false,
                all, "Déclaration TVA", 2);
        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, "DECOMPTE", false,
                all, "Décompte (selon cas)", 3);
        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, "CERTIFICAT_UTILISATION", false,
                EnumSet.of(TypeFichierAutorise.PDF, TypeFichierAutorise.IMAGE),
                "Certificat d'utilisation signé et cacheté par le Président", 4);
        seedDocReq(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, "QUITTANCE_DGI", false,
                EnumSet.of(TypeFichierAutorise.PDF, TypeFichierAutorise.IMAGE),
                "Quittance de la DGI attestant le paiement", 5);

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
}
