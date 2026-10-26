package mr.gov.finances.sgci;

import mr.gov.finances.sgci.service.MinioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Le chemin stocké d'un document est une URL ; la lecture du stockage attend une clé.
 *
 * <p>Le téléchargement par identifiant passait le chemin tel quel, et échouait dans les deux modes de
 * stockage. Test unitaire : la conversion ne dépend que de la configuration.
 */
class MinioCleDepuisCheminTest {

    @SuppressWarnings("unchecked")
    private static MinioService service() {
        return new MinioService(mock(ObjectProvider.class), "minio", "uploads", "documents",
                "http://minio:9000/");
    }

    @Test
    void stockageLocal_laCleEstRangeeSousDocuments() {
        assertThat(service().cleDepuisChemin("/api/local-files/abc_piece.pdf"))
                .isEqualTo("documents/abc_piece.pdf");
    }

    @Test
    void minio_laCleEstCeQuiSuitLeBucket() {
        assertThat(service().cleDepuisChemin("http://minio:9000/documents/abc_piece.pdf"))
                .isEqualTo("abc_piece.pdf");
    }

    @Test
    void uneCleResteUneCle() {
        assertThat(service().cleDepuisChemin("signatures/abc_sig.png")).isEqualTo("signatures/abc_sig.png");
    }

    @Test
    void unCheminVideResteVide() {
        assertThat(service().cleDepuisChemin(null)).isNull();
        assertThat(service().cleDepuisChemin("")).isEmpty();
    }
}
