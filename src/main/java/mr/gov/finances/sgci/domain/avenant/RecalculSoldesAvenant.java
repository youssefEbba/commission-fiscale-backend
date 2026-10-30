package mr.gov.finances.sgci.domain.avenant;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Le recalcul des soldes d'un crédit à l'application d'un avenant.
 *
 * <p>La règle de l'équipe : le montant déjà consommé est conservé tel quel, et le restant de
 * l'ancien crédit est remplacé par le nouveau montant disponible. Pour chacune des trois
 * enveloppes, nouveau solde = nouveau montant accordé − déjà consommé, où le consommé se lit
 * « accordé − restant », comme dans {@code EngagementEtatService}. Cette lecture vaut aussi pour les
 * crédits repris d'archives, qui n'ont pas d'historique d'utilisations derrière leurs soldes.
 *
 * <ul>
 *   <li><b>Cordon</b> (droits et taxes de douane hors TVA, b + c) : l'ancien accordé est le cordon
 *       moins la TVA à l'importation accordée.</li>
 *   <li><b>Quota de TVA à l'importation</b> (d) : accordé moins restant.</li>
 *   <li><b>TVA intérieure</b> (h = g − d) : un consommé négatif — un report à nouveau déjà crédité
 *       à l'entreprise — est conservé, donc reste acquis.</li>
 * </ul>
 *
 * <p>Un nouveau montant inférieur au consommé rendrait un solde négatif : refusé, avec l'écart.
 */
public final class RecalculSoldesAvenant {

    /** L'état du crédit avant l'avenant. Les valeurs nulles (archives) se lisent zéro. */
    public record EtatCredit(BigDecimal montantCordon,
                             BigDecimal tvaImportationAccordee,
                             BigDecimal soldeCordon,
                             BigDecimal tvaImportationRestante,
                             BigDecimal montantTVAInterieure,
                             BigDecimal soldeTVA) {
    }

    /** Le nouveau crédit saisi par la DGTCP : b, c, d accordé et h. */
    public record NouveauCredit(BigDecimal droitsEtTaxesDouaneHorsTva,
                                BigDecimal taxesConsommation,
                                BigDecimal tvaImportationAccordee,
                                BigDecimal montantTVAInterieure) {
    }

    public record Soldes(BigDecimal soldeCordon, BigDecimal tvaImportationRestante, BigDecimal soldeTVA) {
    }

    private RecalculSoldesAvenant() {
    }

    public static Soldes recalculer(EtatCredit ancien, NouveauCredit nouveau) {
        // Sans TVA à l'import accordée (archives), le quota restant tient lieu d'accordé : rien
        // n'est réputé consommé sur une enveloppe dont on ignore l'histoire.
        BigDecimal ancienDAccorde = ancien.tvaImportationAccordee() != null
                ? ancien.tvaImportationAccordee() : nz(ancien.tvaImportationRestante());

        BigDecimal consommeCordon = nz(ancien.montantCordon()).subtract(ancienDAccorde).subtract(nz(ancien.soldeCordon()));
        BigDecimal consommeD = ancienDAccorde.subtract(nz(ancien.tvaImportationRestante()));
        BigDecimal consommeTVA = nz(ancien.montantTVAInterieure()).subtract(nz(ancien.soldeTVA()));

        BigDecimal soldeCordon = nz(nouveau.droitsEtTaxesDouaneHorsTva()).add(nz(nouveau.taxesConsommation()))
                .subtract(consommeCordon);
        BigDecimal resteD = nz(nouveau.tvaImportationAccordee()).subtract(consommeD);
        BigDecimal soldeTVA = nz(nouveau.montantTVAInterieure()).subtract(consommeTVA);

        exigerPositif(soldeCordon, "cordon (droits et taxes de douane)", consommeCordon);
        exigerPositif(resteD, "quota de TVA à l'importation", consommeD);
        exigerPositif(soldeTVA, "TVA intérieure", consommeTVA);
        return new Soldes(soldeCordon, resteD, soldeTVA);
    }

    private static void exigerPositif(BigDecimal solde, String enveloppe, BigDecimal consomme) {
        if (solde.signum() < 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Avenant inapplicable : le nouveau montant de l'enveloppe " + enveloppe
                            + " est inférieur au montant déjà consommé ("
                            + consomme.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            + "). Il manque " + solde.negate().setScale(2, RoundingMode.HALF_UP).toPlainString() + ".");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
