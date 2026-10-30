package mr.gov.finances.sgci.domain.reference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le format des références lisibles : {@code PREFIXE-0010-10-2026}, et la conversion depuis l'ancien.
 *
 * <p>L'équipe a demandé quatre chiffres et des tirets à la place de {@code DC-109-10/2026}. Les
 * références déjà attribuées sont converties, et un code saisi à l'ancien format — sur un papier ou
 * un QR code imprimé avant — doit encore être reconnu.
 */
class FormatReferenceTest {

    @Test
    void formatAvecMois() {
        assertThat(FormatReference.avecMois("DC", 10, 10, 2026)).isEqualTo("DC-0010-10-2026");
        assertThat(FormatReference.avecMois("RT", 1, 3, 2026)).isEqualTo("RT-0001-03-2026");
    }

    @Test
    void formatSansMois() {
        assertThat(FormatReference.sansMois("CU", 7, 2026)).isEqualTo("CU-0007-2026");
    }

    @Test
    void lAncienFormatEstConverti() {
        assertThat(FormatReference.convertirAncienFormat("DC-109-10/2026")).isEqualTo("DC-0109-10-2026");
        assertThat(FormatReference.convertirAncienFormat("CR-052-10/2026")).isEqualTo("CR-0052-10-2026");
        assertThat(FormatReference.convertirAncienFormat("DU-1234-10/2026")).isEqualTo("DU-1234-10-2026");
        assertThat(FormatReference.convertirAncienFormat("CU-007/2026")).isEqualTo("CU-0007-2026");
    }

    @Test
    void ceQuiNEstPasAncienResteTelQuel() {
        assertThat(FormatReference.convertirAncienFormat("DC-0109-10-2026")).isEqualTo("DC-0109-10-2026");
        assertThat(FormatReference.convertirAncienFormat("CERT-1700000000-ABCD1234")).isEqualTo("CERT-1700000000-ABCD1234");
        assertThat(FormatReference.convertirAncienFormat("DC-1/2")).isEqualTo("DC-1/2");
        assertThat(FormatReference.convertirAncienFormat(null)).isNull();
    }

    @Test
    void laConversionToleretLesEspacesEtLaCasse() {
        assertThat(FormatReference.convertirAncienFormat("  dc-109-10/2026 ")).isEqualTo("DC-0109-10-2026");
    }
}
