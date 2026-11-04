package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.maincourante.MainCouranteService;
import mr.gov.finances.sgci.web.dto.maincourante.LigneMainCouranteDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Une mise en place envoyée attend sa prise en charge par la DGI, la DGD ou la DGTCP. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MainCourantePriseEnChargeIT {

    @Autowired private MainCouranteService mainCouranteService;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private mr.gov.finances.sgci.service.ClotureCreditService clotureCreditService;
    @Autowired private mr.gov.finances.sgci.repository.ClotureCreditRepository clotureRepository;
    @Autowired private mr.gov.finances.sgci.repository.SousTraitanceRepository sousTraitanceRepository;

    @Test
    void uneMiseEnPlaceEnvoyeeApparaitPourEtrePriseEnCharge() {
        long n = System.nanoTime();
        CertificatCredit envoye = certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CERT-ENV-" + n).reference("CR-ENV-" + n)
                .statut(StatutCertificat.ENVOYEE)
                .entreprise(entrepriseRepository.findAll().get(0))
                .dateEmission(Instant.now())
                .montantCordon(BigDecimal.ZERO).montantTVAInterieure(BigDecimal.ZERO)
                .soldeCordon(BigDecimal.ZERO).soldeTVA(BigDecimal.ZERO).tvaImportationDouane(BigDecimal.ZERO)
                .build());

        for (Role role : List.of(Role.DGI, Role.DGD, Role.DGTCP)) {
            assertThat(lignes(role.name().toLowerCase(), role))
                    .as(role + " voit la mise en place à prendre en charge")
                    .anySatisfy(l -> {
                        assertThat(l.getId()).isEqualTo(envoye.getId());
                        assertThat(l.getAction()).isEqualTo("Prendre en charge la mise en place");
                    });
        }
        assertThat(lignes("dgb", Role.DGB)).extracting(LigneMainCouranteDto::getId).doesNotContain(envoye.getId());
    }

    @Test
    void uneSousTraitanceDemandeeApparaitChezLaDgtcp() {
        long n = System.nanoTime();
        CertificatCredit ouvert = certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CERT-ST-" + n).reference("CR-ST-" + n)
                .statut(StatutCertificat.OUVERT)
                .entreprise(entrepriseRepository.findAll().get(0))
                .dateEmission(Instant.now())
                .montantCordon(BigDecimal.ZERO).montantTVAInterieure(BigDecimal.ZERO)
                .soldeCordon(BigDecimal.ZERO).soldeTVA(BigDecimal.ZERO).tvaImportationDouane(BigDecimal.ZERO)
                .build());
        var st = sousTraitanceRepository.saveAndFlush(mr.gov.finances.sgci.domain.entity.SousTraitance.builder()
                .certificatCredit(ouvert).sousTraitantEntreprise(entrepriseRepository.findAll().get(0))
                .statut(mr.gov.finances.sgci.domain.enums.StatutSousTraitance.DEMANDE).build());

        Utilisateur u = utilisateurRepository.findByUsername("dgtcp").orElseThrow();
        assertThat(mainCouranteService.pourMoi(new AuthenticatedUser(u.getId(), u.getUsername(), Role.DGTCP)).getLignes())
                .anySatisfy(l -> {
                    assertThat(l.getType()).isEqualTo("SousTraitance");
                    assertThat(l.getId()).isEqualTo(st.getId());
                    assertThat(l.getReference()).isEqualTo("CR-ST-" + n);
                });
    }

    @Test
    void unePropositionDeClotureApparaitChezLePresidentPuisChezLaDgtcp() {
        long n = System.nanoTime();
        CertificatCredit c = certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CERT-CL-" + n).reference("CR-CL-" + n).statut(StatutCertificat.OUVERT)
                .entreprise(entrepriseRepository.findAll().get(0)).dateEmission(Instant.now())
                .montantCordon(BigDecimal.ZERO).montantTVAInterieure(BigDecimal.ZERO)
                .soldeCordon(BigDecimal.ZERO).soldeTVA(BigDecimal.ZERO).tvaImportationDouane(BigDecimal.ZERO)
                .build());
        var cc = clotureRepository.saveAndFlush(mr.gov.finances.sgci.domain.entity.ClotureCredit.builder()
                .certificatCredit(c).dateProposition(Instant.now()).build());

        assertThat(toutes("president", Role.PRESIDENT)).anySatisfy(l -> {
            assertThat(l.getType()).isEqualTo("ClotureCredit");
            assertThat(l.getId()).isEqualTo(cc.getId());
        });
        cc.setApprouvee(true);
        clotureRepository.saveAndFlush(cc);
        assertThat(toutes("president", Role.PRESIDENT)).noneMatch(l -> "ClotureCredit".equals(l.getType()) && cc.getId().equals(l.getId()));
        assertThat(toutes("dgtcp", Role.DGTCP)).anySatisfy(l -> {
            assertThat(l.getAction()).isEqualTo("Finaliser la clôture du crédit");
            assertThat(l.getId()).isEqualTo(cc.getId());
        });
    }

    @Test
    void uneClotureApprouveeNEstPlusProposable() {
        long n = System.nanoTime();
        CertificatCredit c = certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CERT-CLA-" + n).reference("CR-CLA-" + n).statut(StatutCertificat.OUVERT)
                .entreprise(entrepriseRepository.findAll().get(0)).dateEmission(Instant.now())
                .montantCordon(BigDecimal.ZERO).montantTVAInterieure(BigDecimal.ZERO)
                .soldeCordon(BigDecimal.ZERO).soldeTVA(BigDecimal.ZERO).tvaImportationDouane(BigDecimal.ZERO)
                .build());
        clotureRepository.saveAndFlush(mr.gov.finances.sgci.domain.entity.ClotureCredit.builder()
                .certificatCredit(c).dateProposition(Instant.now()).approuvee(true).build());
        Utilisateur u = utilisateurRepository.findByUsername("dgtcp").orElseThrow();
        AuthenticatedUser dgtcp = new AuthenticatedUser(u.getId(), u.getUsername(), Role.DGTCP);

        assertThat(clotureCreditService.findClotureQueue(dgtcp))
                .extracting(mr.gov.finances.sgci.web.dto.CertificatClotureQueueItemDto::getCertificatCreditId)
                .doesNotContain(c.getId());
        var req = new mr.gov.finances.sgci.web.dto.CreateClotureCreditRequest();
        req.setCertificatCreditId(c.getId());
        req.setMotif(mr.gov.finances.sgci.domain.enums.MotifCloture.RECEPTION_DEFINITIVE);
        req.setTypeOperation(mr.gov.finances.sgci.domain.enums.TypeOperationCloture.CLOTURE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> clotureCreditService.proposer(req, dgtcp))
                .hasMessageContaining("déjà approuvée");
    }

    private List<LigneMainCouranteDto> toutes(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return mainCouranteService.pourMoi(new AuthenticatedUser(u.getId(), u.getUsername(), role)).getLignes();
    }

    private List<LigneMainCouranteDto> lignes(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return mainCouranteService.pourMoi(new AuthenticatedUser(u.getId(), u.getUsername(), role)).getLignes().stream()
                .filter(l -> "MISE_EN_PLACE".equals(l.getProcessus())).toList();
    }
}
