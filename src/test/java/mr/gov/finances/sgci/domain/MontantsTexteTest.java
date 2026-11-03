package mr.gov.finances.sgci.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** A48 : la file de clôture affichait « 495277.8900 MRU ». */
class MontantsTexteTest {

    @Test
    void auFormatDesEcrans() {
        assertThat(MontantsTexte.mru(new BigDecimal("495277.8900"))).isEqualTo("495 277,89 MRU");
        assertThat(MontantsTexte.mru(new BigDecimal("1200000000"))).isEqualTo("1 200 000 000,00 MRU");
        assertThat(MontantsTexte.montant(new BigDecimal("0.005"))).isEqualTo("0,01");
        assertThat(MontantsTexte.montant(new BigDecimal("-1234.5"))).isEqualTo("-1 234,50");
        assertThat(MontantsTexte.montant(null)).isEqualTo("—");
    }
}
