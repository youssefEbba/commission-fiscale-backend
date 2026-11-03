package mr.gov.finances.sgci.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Un nom concaténé dans une requête de migration est un identifiant simple, ou rien. */
class IdentifiantSqlTest {

    @Test
    void unIdentifiantSimplePasse() {
        assertThat(IdentifiantSql.sur("demande_correction")).isEqualTo("demande_correction");
        assertThat(IdentifiantSql.sur("FK1a2b3c")).isEqualTo("FK1a2b3c");
    }

    @Test
    void toutAutreTexteEstRefuse() {
        for (String injection : new String[] {"t; DROP TABLE utilisateur", "a b", "x' OR '1'='1", "`t`", "", "1table"}) {
            assertThatThrownBy(() -> IdentifiantSql.sur(injection)).as(injection)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> IdentifiantSql.sur(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
