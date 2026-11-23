package mr.gov.finances.sgci;

import mr.gov.finances.sgci.config.ReferenceBackfillMigration;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.ClotureCredit;
import mr.gov.finances.sgci.domain.entity.SousTraitance;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ClotureCreditRepository;
import mr.gov.finances.sgci.repository.SousTraitanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toute demande a une référence lisible, que l'écran affiche à la place de l'id technique : la
 * sous-traitance ({@code ST-…}) et la proposition de clôture ({@code CL-…}) n'en avaient pas.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReferencesLisiblesIT {

    @Autowired private ReferenceBackfillMigration migration;
    @Autowired private SousTraitanceRepository sousTraitanceRepository;
    @Autowired private ClotureCreditRepository clotureRepository;
    @Autowired private CertificatCreditRepository certificatRepository;

    @Test
    @Transactional
    void lesSousTraitancesEtClotureSansReferenceEnRecoiventUneAuDemarrage() {
        CertificatCredit cert = certificatRepository.findByNumero("CI-DEMO-SCEN-E").orElseThrow();
        SousTraitance st = sousTraitanceRepository.save(SousTraitance.builder().certificatCredit(cert).build());
        ClotureCredit cc = new ClotureCredit();
        cc.setCertificatCredit(cert);
        cc.setDateProposition(Instant.now());
        cc = clotureRepository.save(cc);
        assertThat(st.getReference()).isNull();
        assertThat(cc.getReference()).isNull();

        migration.migrateIfNeeded();

        assertThat(sousTraitanceRepository.findById(st.getId()).orElseThrow().getReference())
                .matches("ST-\\d{4}-\\d{2}-\\d{4}");
        assertThat(clotureRepository.findById(cc.getId()).orElseThrow().getReference())
                .matches("CL-\\d{4}-\\d{2}-\\d{4}");
    }
}
