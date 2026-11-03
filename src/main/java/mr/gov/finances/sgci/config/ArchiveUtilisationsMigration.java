package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.service.archive.ImportArchiveCreditService;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Utilisations reprises de l'archive avant le 03/11/2026 : la douane y était « apurée » au lieu de
 * « liquidée », et la TVA intérieure sans son montant de TVA. Le détail du crédit comptait donc zéro
 * importation liquidée et affichait une colonne TVA vide. Corrigé au démarrage, sans le fichier
 * d'origine ; une utilisation déjà conforme n'est pas touchée.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(120)
public class ArchiveUtilisationsMigration implements ApplicationRunner {

    private final UtilisationCreditRepository utilisationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int corrigees = 0;
        for (UtilisationCredit u : utilisationRepository.findByOrigineArchiveLibelleIsNotNull()) {
            if (ImportArchiveCreditService.completerUtilisationArchive(u)) {
                utilisationRepository.save(u);
                corrigees++;
            }
        }
        if (corrigees > 0) {
            log.info("Reprise d'archive : {} utilisation(s) mise(s) au format de l'application", corrigees);
        }
    }
}
