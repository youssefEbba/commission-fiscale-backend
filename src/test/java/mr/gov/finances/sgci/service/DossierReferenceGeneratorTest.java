package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Marche;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DossierReferenceGeneratorTest {

    @Test
    void buildReference_avecMarche_utiliseLettreAcDateEtSlugMarche() {
        Instant depot = LocalDate.of(2026, 6, 17).atStartOfDay().toInstant(ZoneOffset.UTC);
        DemandeCorrection dc = DemandeCorrection.builder()
                .dateDepot(depot)
                .autoriteContractante(AutoriteContractante.builder().code("AC_DEFAULT").nom("Ministère").build())
                .marche(Marche.builder().numeroMarche("MP-DEMO-SCEN-A").build())
                .build();

        assertThat(DossierReferenceGenerator.buildReference(dc))
                .isEqualTo("DOSS-A-20260617-SCENA");
    }

    @Test
    void buildReference_sansMarche_utiliseReferenceConvention() {
        Instant depot = LocalDate.of(2026, 1, 5).atStartOfDay().toInstant(ZoneOffset.UTC);
        DemandeCorrection dc = DemandeCorrection.builder()
                .dateDepot(depot)
                .autoriteContractante(AutoriteContractante.builder().code("AC-MINH").build())
                .convention(Convention.builder().reference("CONV-2026-ROUTE").build())
                .build();

        assertThat(DossierReferenceGenerator.buildReference(dc))
                .isEqualTo("DOSS-M-20260105-ROUTE");
    }

    @Test
    void slugFromHyphenatedCode_dernierSegmentLong() {
        assertThat(DossierReferenceGenerator.slugFromHyphenatedCode("MP-DEMO-DEFAULT-VOIRIE"))
                .isEqualTo("VOIRIE");
    }

    @Test
    void ensureUnique_ajouteSuffixeSiCollision() {
        Set<String> existing = new HashSet<>();
        existing.add("DOSS-A-20260617-SCENA");

        String unique = DossierReferenceGenerator.ensureUnique(
                "DOSS-A-20260617-SCENA",
                existing::contains);

        assertThat(unique).isEqualTo("DOSS-A-20260617-SCENA-02");
    }
}
