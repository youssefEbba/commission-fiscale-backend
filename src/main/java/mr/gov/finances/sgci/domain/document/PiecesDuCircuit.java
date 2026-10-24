package mr.gov.finances.sgci.domain.document;

import java.util.Set;

/**
 * Les pièces que le circuit lui-même exige, nommément : elles ne se désactivent ni ne se suppriment
 * depuis le paramétrage de la GED.
 *
 * <p>Le paramétrage permet de retirer une pièce d'un processus. Pour la plupart, c'est sans risque :
 * la pièce cesse d'être demandée. Celles-ci, en revanche, sont attendues par le code des étapes. Les
 * retirer ne les rendrait pas facultatives : leur dépôt deviendrait impossible, et l'étape qui les
 * exige resterait bloquée pour tous les dossiers.
 *
 * <ul>
 *   <li>{@code LETTRE_ADOPTION} — exigée pour que le Président adopte une demande de correction
 *       ({@code DemandeCorrectionService}, {@code DecisionCorrectionService}).</li>
 *   <li>{@code OFFRE_FISCALE_CORRIGEE}, {@code CREDIT_INTERIEUR} — exigées, selon le dossier, pour
 *       les visas de la DGD et de la DGI ({@code DecisionCorrectionService}).</li>
 *   <li>{@code RECU_DEPOT} — produit au dépôt de la demande de correction ({@code DocumentService}).</li>
 *   <li>{@code CERTIFICAT_CREDIT_IMPOTS} — exigé pour la validation du crédit par le Président
 *       ({@code CertificatCreditService}, {@code DecisionCertificatCreditService}).</li>
 *   <li>{@code CERTIFICAT_UTILISATION} — émis par le Président sur chaque utilisation
 *       ({@code UtilisationCreditService}, {@code DocumentUtilisationCreditService}).</li>
 *   <li>{@code BULLETIN_ANNOTE}, {@code CHEQUE_CERTIFIE}, {@code QUITTANCE_TRESOR} — les étapes de
 *       l'utilisation en douane ; {@code QUITTANCE_DGI} — celle de la TVA intérieure
 *       ({@code UtilisationCreditService}).</li>
 * </ul>
 *
 * <p>La règle porte sur le code, quel que soit le processus : ces codes n'ont de sens que dans le
 * circuit qui les produit, et la liste reste ainsi lisible d'un seul coup d'œil. Un code ajouté au
 * circuit doit être ajouté ici — faute de quoi il redeviendrait désactivable sans que rien ne le
 * signale.
 */
public final class PiecesDuCircuit {

    public static final Set<String> CODES = Set.of(
            "LETTRE_ADOPTION",
            "OFFRE_FISCALE_CORRIGEE",
            "CREDIT_INTERIEUR",
            "RECU_DEPOT",
            "CERTIFICAT_CREDIT_IMPOTS",
            "CERTIFICAT_UTILISATION",
            "BULLETIN_ANNOTE",
            "CHEQUE_CERTIFIE",
            "QUITTANCE_TRESOR",
            "QUITTANCE_DGI");

    private PiecesDuCircuit() {
    }

    public static boolean exigeeParLeCircuit(String codeDocument) {
        return codeDocument != null && CODES.contains(codeDocument);
    }
}
