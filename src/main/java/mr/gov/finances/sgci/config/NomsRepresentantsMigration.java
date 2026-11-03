package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.repository.UtilisateurRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Les comptes des directions s'appellent « Représentant DGD », « Représentant DGI »… et non plus
 * « Agent … » (novembre 2026). Seuls les noms encore égaux à l'ancien nom semé sont renommés : un
 * nom saisi par l'administrateur n'est pas touché.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(115)
public class NomsRepresentantsMigration implements ApplicationRunner {

    static final Map<String, String> ANCIENS_NOMS = Map.of(
            "Agent DGD", "Représentant DGD",
            "Agent DGTCP", "Représentant DGTCP",
            "Agent DGI", "Représentant DGI",
            "Agent DGB", "Représentant DGB");

    private final UtilisateurRepository utilisateurRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int renommes = 0;
        for (Utilisateur u : utilisateurRepository.findByNomCompletIn(ANCIENS_NOMS.keySet())) {
            u.setNomComplet(ANCIENS_NOMS.get(u.getNomComplet()));
            utilisateurRepository.save(u);
            renommes++;
        }
        if (renommes > 0) {
            log.info("{} compte(s) de direction renommé(s) « Représentant … »", renommes);
        }
    }
}
