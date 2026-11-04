package mr.gov.finances.sgci;

import mr.gov.finances.sgci.web.controller.LocalFileController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/** Un PDF ou une image s'ouvre dans un onglet ; rien d'autre n'est servi avec un type exécutable. */
class FichierTypeAffichableTest {

    @Test
    void lesPdfEtImagesSontAffichables() {
        assertThat(LocalFileController.typeAffichable("e803_mandat_exemple.pdf")).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(LocalFileController.typeAffichable("SCAN.PDF")).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(LocalFileController.typeAffichable("photo.jpeg")).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(LocalFileController.typeAffichable("plan.png")).isEqualTo(MediaType.IMAGE_PNG);
    }

    @Test
    void htmlSvgEtInconnusRestentBinaires() {
        assertThat(LocalFileController.typeAffichable("piege.html")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(LocalFileController.typeAffichable("logo.svg")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(LocalFileController.typeAffichable("budget.xlsx")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(LocalFileController.typeAffichable(null)).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }
}
