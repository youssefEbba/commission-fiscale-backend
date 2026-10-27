package mr.gov.finances.sgci;

import mr.gov.finances.sgci.config.DataInitializer;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Un redémarrage ne recrée pas la demande de démonstration, même quand elle a changé d'entreprise.
 *
 * <p>Incident du 2026-10-27 en production : la demande {@code DC-DEFAULT-DEMO} existait, mais plus
 * sous l'entreprise {@code NIF_DEFAULT}. Le seed ne la cherchait que là : il la recréait, MySQL
 * refusait le doublon de numéro, l'application s'arrêtait et Docker la relançait — plus de deux mille
 * fois. Chaque tentative laissait en outre un marché de démonstration orphelin, enregistré juste
 * avant la demande.
 *
 * <p>Sans transaction de test, comme au démarrage réel : chaque enregistrement y est validé
 * aussitôt. L'état modifié est donc restauré à la main.
 */
@SpringBootTest
@ActiveProfiles("test")
class DemoSeedRedemarrageIT {

    private static final String DEMO_DC = "DC-DEFAULT-DEMO";
    private static final String DEMO_MARCHE = "MP-DEMO-DEFAULT-VOIRIE";

    @Autowired private DataInitializer dataInitializer;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private MarcheRepository marcheRepository;

    @Test
    void laDemandeDeDemoRattacheeAUneAutreEntrepriseNEstPasRecreee() {
        DemandeCorrection demo = demandeRepository.findByNumero(DEMO_DC).orElseThrow();
        Entreprise origine = demo.getEntreprise();
        Entreprise autre = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Entreprise reprise " + System.nanoTime())
                .nif("NIF-REPRISE-" + System.nanoTime())
                .build());
        long marchesAvant = marchesDeDemo();
        try {
            demo.setEntreprise(autre);
            demandeRepository.saveAndFlush(demo);

            assertThatCode(() -> dataInitializer.run()).doesNotThrowAnyException();

            assertThat(demandeRepository.findByNumero(DEMO_DC)).get()
                    .extracting(d -> d.getEntreprise().getId())
                    .as("la demande existante est laissée telle quelle")
                    .isEqualTo(autre.getId());
            assertThat(marchesDeDemo())
                    .as("aucun marché de démonstration orphelin")
                    .isEqualTo(marchesAvant);
        } finally {
            DemandeCorrection restauree = demandeRepository.findByNumero(DEMO_DC).orElseThrow();
            restauree.setEntreprise(origine);
            demandeRepository.saveAndFlush(restauree);
            entrepriseRepository.delete(autre);
        }
    }

    private long marchesDeDemo() {
        return marcheRepository.findAll().stream()
                .filter(m -> DEMO_MARCHE.equals(m.getNumeroMarche()))
                .count();
    }
}
