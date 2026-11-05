package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutSousTraitance;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.SousTraitanceRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.SousTraitanceService;
import mr.gov.finances.sgci.web.dto.CreateSousTraitanceRequest;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Une nouvelle demande ne doit pas écraser une sous-traitance déjà autorisée ou en cours. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SousTraitanceRemplacementIT {

    @Autowired private SousTraitanceService service;
    @Autowired private SousTraitanceRepository repository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    @Test
    void uneSousTraitanceAutoriseeNEstPasEcraseeMaisUneRefuseeEstRemplacable() {
        Utilisateur u = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        Entreprise titulaire = u.getEntreprise();
        Entreprise autre = entrepriseRepository.findAll().stream()
                .filter(e -> !e.getId().equals(titulaire.getId())).findFirst().orElseThrow();
        long n = System.nanoTime();
        CertificatCredit c = certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CERT-STR-" + n).reference("CR-STR-" + n).statut(StatutCertificat.OUVERT)
                .entreprise(titulaire).dateEmission(Instant.now())
                .montantCordon(BigDecimal.ZERO).montantTVAInterieure(BigDecimal.ZERO)
                .soldeCordon(BigDecimal.ZERO).soldeTVA(BigDecimal.ZERO).tvaImportationDouane(BigDecimal.ZERO)
                .build());
        AuthenticatedUser ent = new AuthenticatedUser(u.getId(), u.getUsername(), Role.ENTREPRISE);
        CreateSousTraitanceRequest req = new CreateSousTraitanceRequest();
        req.setCertificatCreditId(c.getId());
        req.setSousTraitantEntrepriseId(autre.getId());
        req.setVolumes(BigDecimal.TEN);
        req.setQuantites(BigDecimal.ONE);

        Long id = service.create(req, ent).getId();
        var st = repository.findById(id).orElseThrow();
        st.setStatut(StatutSousTraitance.AUTORISEE);
        repository.saveAndFlush(st);

        assertThatThrownBy(() -> service.create(req, ent))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà autorisée");
        assertThat(repository.findById(id).orElseThrow().getStatut()).isEqualTo(StatutSousTraitance.AUTORISEE);

        st = repository.findById(id).orElseThrow();
        st.setStatut(StatutSousTraitance.REFUSEE);
        repository.saveAndFlush(st);
        assertThat(service.create(req, ent).getStatut()).isEqualTo(StatutSousTraitance.DEMANDE);
    }
}
