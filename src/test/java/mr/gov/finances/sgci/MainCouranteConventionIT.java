package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.maincourante.MainCouranteService;
import mr.gov.finances.sgci.web.dto.maincourante.LigneMainCouranteDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Une convention déposée attend la DGB ou la DGI : elle doit figurer dans leur main courante. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MainCouranteConventionIT {

    @Autowired private MainCouranteService mainCouranteService;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private AutoriteContractanteRepository autoriteContractanteRepository;

    @Test
    void uneConventionEnAttenteApparaitChezCeuxQuiLaValident() {
        AutoriteContractante ac = autoriteContractanteRepository.findAll().get(0);
        Convention attente = conventionRepository.save(Convention.builder().autoriteContractante(ac)
                .reference("MC-CONV-ATTENTE").intitule("Convention à valider").build());
        Convention validee = conventionRepository.save(Convention.builder().autoriteContractante(ac)
                .reference("MC-CONV-VALIDEE").intitule("Convention validée").statut(StatutConvention.VALIDE).build());

        List<LigneMainCouranteDto> dgb = conventions("dgb", Role.DGB);
        assertThat(dgb).extracting(LigneMainCouranteDto::getId).contains(attente.getId()).doesNotContain(validee.getId());
        assertThat(dgb).filteredOn(l -> l.getId().equals(attente.getId())).first()
                .satisfies(l -> {
                    assertThat(l.getType()).isEqualTo("Convention");
                    assertThat(l.getAction()).isEqualTo("Valider ou rejeter la convention");
                });
        assertThat(conventions("dgi", Role.DGI)).extracting(LigneMainCouranteDto::getId).contains(attente.getId());
        assertThat(conventions("dgd", Role.DGD)).as("la DGD ne valide pas les conventions").isEmpty();
    }

    private List<LigneMainCouranteDto> conventions(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return mainCouranteService.pourMoi(new AuthenticatedUser(u.getId(), u.getUsername(), role)).getLignes().stream()
                .filter(l -> "CONVENTION".equals(l.getProcessus())).toList();
    }
}
