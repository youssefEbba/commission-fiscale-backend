package mr.gov.finances.sgci.domain.document;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Libellés français des types de document système.
 *
 * <p>Le référentiel était amorcé avec un libellé tiré du nom de l'énumération (« Piece Identite »,
 * « Declaration Douane »), sans accents ni articles (anomalie A09). Cette table donne le libellé
 * attendu ; un type absent garde le libellé dérivé de son code.
 */
public final class LibellesTypeDocument {

    private static final Map<String, String> LIBELLES = Map.ofEntries(
            Map.entry("CONVENTION_JOIGNED_DOCUMENT", "Document joint à la convention"),
            Map.entry("CERTIFICAT_CREDIT_IMPOTS", "Certificat de crédit d'impôt"),
            Map.entry("DEMANDE_UTILISATION", "Demande d'utilisation"),
            Map.entry("DEMANDE_MOTIVEE_TRANSFERT", "Demande motivée de transfert"),
            Map.entry("DECLARATION_CLOTURE_DOUANE", "Déclaration de clôture des opérations en douane"),
            Map.entry("JUSTIFICATIFS_CLOTURE_DOUANE", "Justificatifs de clôture en douane"),
            Map.entry("CONTRAT_SOUS_TRAITANCE_ENREGISTRE", "Contrat de sous-traitance enregistré"),
            Map.entry("LETTRE_SOUS_TRAITANCE", "Lettre de sous-traitance (volumes)"),
            Map.entry("FEUILLE_EVALUATION_SIGNEE", "Feuille d'évaluation signée"),
            Map.entry("OFFRE_FISCALE", "Offre fiscale"),
            Map.entry("OFFRE_FISCALE_CORRIGEE", "Offre fiscale corrigée"),
            Map.entry("CREDIT_INTERIEUR", "Crédit d'impôt intérieur"),
            Map.entry("CREDIT_EXTERIEUR", "Crédit d'impôt extérieur"),
            Map.entry("OFFRE_CORRIGEE", "Offre corrigée"),
            Map.entry("LETTRE_SAISINE", "Lettre de saisine"),
            Map.entry("PV_OUVERTURE", "Procès-verbal d'ouverture des offres"),
            Map.entry("ATTESTATION_FISCALE", "Attestation fiscale"),
            Map.entry("OFFRE_FINANCIERE", "Offre financière"),
            Map.entry("TABLEAU_MODELE", "Tableau modèle"),
            Map.entry("DAO_DQE", "DAO et DQE"),
            Map.entry("LISTE_ITEMS", "Liste des items"),
            Map.entry("DAO_ANNOTE", "DAO annoté"),
            Map.entry("CERTIFICAT_VISITE_DOUANE", "Certificat de visite douanière"),
            Map.entry("CERTIFICAT_CREDIT_IMPOTS_SYDONIA", "Certificat de crédit d'impôt (SYDONIA)"),
            Map.entry("LETTRE_DEMANDE_CREDIT_IMPOTS", "Lettre de demande de crédit d'impôt"),
            Map.entry("DECLARATION_TVA", "Déclaration de TVA"),
            Map.entry("ORDRE_TRANSIT", "Ordre de transit"),
            Map.entry("IMAGE_DECLARATION_DOUANE", "Image de la déclaration en douane"),
            Map.entry("DEVIS", "Devis"),
            Map.entry("LETTRE_DEMANDE_MISE_EN_PLACE_CI", "Lettre de demande de mise en place du crédit d'impôt"),
            Map.entry("LETTRE_NOTIFICATION_CONTRAT", "Lettre de notification du marché"),
            Map.entry("CONTRAT", "Contrat enregistré"),
            Map.entry("CERTIFICAT_NIF", "Certificat NIF"),
            Map.entry("LETTRE_CORRECTION", "Lettre de correction"),
            Map.entry("LETTRE_ADOPTION", "Lettre d'adoption"),
            Map.entry("BULLETIN_LIQUIDATION", "Bulletin de liquidation"),
            Map.entry("BULLETIN_ANNOTE", "Bulletin annoté"),
            Map.entry("CHEQUE_CERTIFIE", "Chèque certifié"),
            Map.entry("QUITTANCE_TRESOR", "Quittance du Trésor"),
            Map.entry("QUITTANCE_DGI", "Quittance de la DGI"),
            Map.entry("DECLARATION_DOUANE", "Déclaration en douane"),
            Map.entry("FACTURE", "Facture"),
            Map.entry("CONNAISSEMENT", "Connaissement / LTA / LVI"),
            Map.entry("DECOMPTE", "Décompte"),
            Map.entry("NOTE_SERVICE", "Note de service"),
            Map.entry("JUSTIFICATIONS_LEGALES", "Justifications légales"),
            Map.entry("LETTRES_MOTIVEES", "Lettres motivées"),
            Map.entry("AVENANT_CONTRAT", "Avenant au contrat"),
            Map.entry("LETTRES_AUTORITE_CONTRACTANTE", "Lettres de l'autorité contractante"),
            Map.entry("DETAIL_CORRECTIONS_NECESSAIRES", "Détail des corrections nécessaires"),
            Map.entry("DOCUMENTS_OFFICIELS", "Documents officiels"),
            Map.entry("DECISION_COMMISSION", "Décision de la Commission"),
            Map.entry("LISTE_CREDITS_A_CLOTURER", "Liste des crédits à clôturer"),
            Map.entry("CERTIFICAT_UTILISATION", "Certificat d'utilisation"),
            Map.entry("RECU_DEPOT", "Reçu de dépôt"),
            Map.entry("PIECE_IDENTITE", "Pièce d'identité"),
            Map.entry("MANDAT_REPRESENTATION", "Mandat de représentation"),
            Map.entry("REGISTRE_COMMERCE", "Registre du commerce"),
            Map.entry("LISTE_EQUIPEMENTS", "Liste des équipements et véhicules sous régime"));

    private LibellesTypeDocument() {
    }

    /** Le libellé français du type, à défaut celui dérivé de son code. */
    public static String libelle(String code) {
        String libelle = LIBELLES.get(code);
        return libelle != null ? libelle : libelleDerive(code);
    }

    /**
     * Le libellé que l'amorçage dérivait du code avant le 02/11/2026 : « PIECE_IDENTITE » donnait
     * « Piece Identite ». Sert à reconnaître les libellés jamais retouchés, seuls à être corrigés.
     */
    public static String libelleDerive(String code) {
        return Arrays.stream(code.split("_"))
                .map(part -> part.length() <= 3 ? part : part.charAt(0) + part.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
