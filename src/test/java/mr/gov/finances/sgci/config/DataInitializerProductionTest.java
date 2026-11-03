package mr.gov.finances.sgci.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * En production, le démarrage ne crée ni compte au mot de passe connu, ni donnée de démonstration.
 *
 * <p>Constat de la revue de sécurité : dix comptes ({@code admin}, {@code president}, {@code dgtcp}…)
 * étaient créés en production avec le mot de passe {@code 123456}, le compte {@code admin} avec
 * {@code admin} s'il manquait, et chaque démarrage réactivait ceux qu'un administrateur avait
 * désactivés. Les demandes et certificats de démonstration étaient eux aussi semés.
 *
 * <p>Test unitaire : les décisions ne dépendent que de la configuration lue.
 */
class DataInitializerProductionTest {

    @Test
    void enProductionLesComptesDeDemonstrationNeSontPasCrees() {
        assertThat(DataInitializer.comptesDeDemonstration(production())).isFalse();
        assertThat(DataInitializer.comptesDeDemonstration(developpement())).isTrue();
    }

    @Test
    void enProductionLesDonneesDeDemonstrationNeSontPasSemees() {
        assertThat(DataInitializer.donneesDeDemonstration(production(), "app.seed.demande-correction.enabled")).isFalse();
        assertThat(DataInitializer.donneesDeDemonstration(developpement(), "app.seed.demande-correction.enabled")).isTrue();
    }

    @Test
    void enProductionLAdministrateurInitialPrendSonMotDePasseDansLEnvironnement() {
        MockEnvironment env = production().withProperty("app.admin.initial-password", "Administrateur-Initial-2026!");

        assertThat(DataInitializer.motDePasseAdministrateurInitial(env)).isEqualTo("Administrateur-Initial-2026!");
    }

    @Test
    void enProductionSansMotDePasseInitialLAdministrateurNEstPasCree() {
        assertThatThrownBy(() -> DataInitializer.motDePasseAdministrateurInitial(production()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ADMIN_INITIAL_PASSWORD");
    }

    @Test
    void enProductionUnMotDePasseInitialFaibleEstRefuse() {
        assertThatThrownBy(() -> DataInitializer.motDePasseAdministrateurInitial(
                production().withProperty("app.admin.initial-password", "123456")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static MockEnvironment production() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        return env;
    }

    private static MockEnvironment developpement() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        return env;
    }
}
