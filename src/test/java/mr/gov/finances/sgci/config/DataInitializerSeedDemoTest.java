package mr.gov.finances.sgci.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Une donnée de démonstration en échec n'arrête plus le démarrage.
 *
 * <p>Le 2026-10-27, un doublon dans une seule d'entre elles a fait redémarrer l'application en boucle
 * en production. Test unitaire : la règle ne dépend que de {@link DataInitializer#seedDemo}.
 */
class DataInitializerSeedDemoTest {

    @Test
    void unEchecEstJournaliseEtLeDemarrageContinue() {
        assertThatCode(() -> DataInitializer.seedDemo("essai", () -> {
            throw new IllegalStateException("Duplicate entry 'DC-DEFAULT-DEMO'");
        })).doesNotThrowAnyException();
    }

    @Test
    void unSeedSansErreurEstBienExecute() {
        AtomicBoolean execute = new AtomicBoolean();

        DataInitializer.seedDemo("essai", () -> execute.set(true));

        assertThat(execute).isTrue();
    }
}
