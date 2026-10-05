package mr.gov.finances.sgci.web.exception;

import org.hibernate.exception.DataException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une panne technique ne doit pas remonter son message à l'écran.
 *
 * <p>Le défaut constaté en recette : la requête {@code UPDATE} complète, noms de colonnes compris,
 * affichée dans une fenêtre d'erreur utilisateur. La reconnaissance se fait sur l'origine de
 * l'exception et non sur son texte — c'est ce que verrouillent ces tests.
 */
class GlobalExceptionHandlerTest {

    @Test
    void uneSqlExceptionEnchaineeEstTechnique() {
        RuntimeException ex = new RuntimeException("could not execute statement",
                new SQLException("Data truncated for column 'statut' at row 1"));

        assertThat(GlobalExceptionHandler.estDOrigineTechnique(ex)).isTrue();
    }

    @Test
    void uneExceptionHibernateEstTechnique() {
        // Le cas exact du bug : Hibernate remonte non traduit, son message porte le SQL.
        DataException hibernate = new DataException("could not execute statement",
                new SQLException("Data truncated for column 'statut' at row 1"));

        assertThat(GlobalExceptionHandler.estDOrigineTechnique(hibernate)).isTrue();
    }

    @Test
    void uneExceptionSpringDaoEstTechnique() {
        assertThat(GlobalExceptionHandler.estDOrigineTechnique(
                new DataIntegrityViolationException("contrainte"))).isTrue();
    }

    @Test
    void uneErreurMetierResteMetier() {
        // Ces messages-là sont écrits pour l'utilisateur : les masquer serait une régression.
        assertThat(GlobalExceptionHandler.estDOrigineTechnique(
                new IllegalStateException("Le chèque certifié est obligatoire"))).isFalse();
        assertThat(GlobalExceptionHandler.estDOrigineTechnique(
                ApiException.conflict(ApiErrorCode.STATUT_INCOMPATIBLE, "Statut incompatible"))).isFalse();
    }
}
