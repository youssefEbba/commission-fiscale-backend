package mr.gov.finances.sgci.domain.avenant;

import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le recalcul des soldes à l'application d'un avenant : le consommé est conservé tel quel, le
 * restant de l'ancien crédit est remplacé par le nouveau montant disponible.
 *
 * <p>Test unitaire : la règle ne dépend que des montants.
 */
class RecalculSoldesAvenantTest {

    /**
     * Ancien crédit : cordon 1000 = b 600 + c 100 + d 300 ; 200 consommés sur b + c (solde 500) ;
     * 50 consommés sur le quota de TVA à l'import (reste 250) ; TVA intérieure 400, 250 consommés
     * (solde 150).
     */
    private static final RecalculSoldesAvenant.EtatCredit ANCIEN = new RecalculSoldesAvenant.EtatCredit(
            bd(1000), bd(300), bd(500), bd(250), bd(400), bd(150));

    @Test
    void leConsommeEstConserveEtLeRestantRemplace() {
        RecalculSoldesAvenant.Soldes s = RecalculSoldesAvenant.recalculer(ANCIEN,
                new RecalculSoldesAvenant.NouveauCredit(bd(800), bd(100), bd(400), bd(500)));

        assertThat(s.soldeCordon()).as("900 accordés − 200 consommés").isEqualByComparingTo("700");
        assertThat(s.tvaImportationRestante()).as("400 accordés − 50 consommés").isEqualByComparingTo("350");
        assertThat(s.soldeTVA()).as("500 accordés − 250 consommés").isEqualByComparingTo("250");
    }

    @Test
    void unAvenantALaBaisseRestePossibleTantQueLeConsommeEstCouvert() {
        RecalculSoldesAvenant.Soldes s = RecalculSoldesAvenant.recalculer(ANCIEN,
                new RecalculSoldesAvenant.NouveauCredit(bd(200), bd(0), bd(50), bd(250)));

        assertThat(s.soldeCordon()).isEqualByComparingTo("0");
        assertThat(s.tvaImportationRestante()).isEqualByComparingTo("0");
        assertThat(s.soldeTVA()).isEqualByComparingTo("0");
    }

    @Test
    void unNouveauCreditInferieurAuConsommeEstRefuse() {
        assertThatThrownBy(() -> RecalculSoldesAvenant.recalculer(ANCIEN,
                new RecalculSoldesAvenant.NouveauCredit(bd(100), bd(0), bd(400), bd(500))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cordon")
                .hasMessageContaining("200");
    }

    @Test
    void leReportANouveauDejaAcquisEstConserve() {
        // Un apurement a pu créditer le solde de TVA intérieure au-delà de l'accordé : le consommé
        // est alors négatif, et ce report reste acquis à l'entreprise.
        RecalculSoldesAvenant.EtatCredit avecReport = new RecalculSoldesAvenant.EtatCredit(
                bd(1000), bd(300), bd(500), bd(250), bd(400), bd(450));

        RecalculSoldesAvenant.Soldes s = RecalculSoldesAvenant.recalculer(avecReport,
                new RecalculSoldesAvenant.NouveauCredit(bd(600), bd(100), bd(300), bd(500)));

        assertThat(s.soldeTVA()).isEqualByComparingTo("550");
    }

    @Test
    void unCreditRepriseDArchiveSansVentilationEstGere() {
        // Archive : ni b, ni d accordé, seulement des montants et des soldes déclarés.
        RecalculSoldesAvenant.EtatCredit archive = new RecalculSoldesAvenant.EtatCredit(
                bd(1000), null, bd(600), null, bd(400), bd(400));

        RecalculSoldesAvenant.Soldes s = RecalculSoldesAvenant.recalculer(archive,
                new RecalculSoldesAvenant.NouveauCredit(bd(1100), bd(0), bd(0), bd(500)));

        assertThat(s.soldeCordon()).as("1100 − 400 consommés").isEqualByComparingTo("700");
        assertThat(s.soldeTVA()).isEqualByComparingTo("500");
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }
}
