package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.maincourante.MainCouranteService;
import mr.gov.finances.sgci.web.dto.maincourante.LigneMainCouranteDto;
import mr.gov.finances.sgci.web.dto.maincourante.MainCouranteDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La main courante montre le n° du crédit de chaque dossier qui en a un, et se filtre par n° de
 * dossier, de crédit ou de marché (remarques 9 et 10 de la recette).
 */
@SpringBootTest
@ActiveProfiles("test")
class MainCouranteRechercheIT {

    @Autowired private MainCouranteService service;
    @Autowired private UtilisateurRepository utilisateurRepository;

    @Test
    @Transactional
    void chaqueLigneAvecUnCreditPorteSonNumeroEtLaRechercheFiltre() {
        AuthenticatedUser acteur = null;
        LigneMainCouranteDto avecCredit = null;
        for (String username : List.of("dgd", "dgtcp", "dgi", "president", "entreprise")) {
            AuthenticatedUser u = compte(username);
            avecCredit = service.pourMoi(u).getLignes().stream()
                    .filter(l -> "UtilisationCredit".equals(l.getType()) || "TransfertCredit".equals(l.getType())
                            || "CertificatCredit".equals(l.getType()))
                    .findFirst().orElse(null);
            if (avecCredit != null) {
                acteur = u;
                break;
            }
        }
        assertThat(avecCredit).as("aucune main courante ne porte de dossier rattaché à un crédit").isNotNull();
        assertThat(avecCredit.getCertificatId()).isNotNull();
        assertThat(avecCredit.getCertificatReference()).isNotBlank();

        String recherche = avecCredit.getCertificatReference().toLowerCase(Locale.ROOT);
        MainCouranteDto filtree = service.pourMoi(acteur, "  " + avecCredit.getCertificatReference().toUpperCase(Locale.ROOT) + " ");
        assertThat(filtree.getLignes()).isNotEmpty()
                .allSatisfy(l -> assertThat(Stream.of(l.getReference(), l.getCertificatReference(),
                                l.getMarcheNumero(), l.getEntrepriseRaisonSociale())
                        .filter(Objects::nonNull).anyMatch(v -> v.toLowerCase(Locale.ROOT).contains(recherche)))
                        .isTrue());
        assertThat(filtree.getTotal()).isEqualTo(filtree.getLignes().size());

        MainCouranteDto vide = service.pourMoi(acteur, "zz-introuvable-" + System.nanoTime());
        assertThat(vide.getLignes()).isEmpty();
        assertThat(vide.getTotal()).isZero();
    }

    private AuthenticatedUser compte(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }
}
