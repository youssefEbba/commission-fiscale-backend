package mr.gov.finances.sgci.service.historique;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.HistoriqueStatut;
import mr.gov.finances.sgci.repository.HistoriqueStatutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Écrit les transitions détectées, dans sa propre transaction.
 *
 * <p>{@code REQUIRES_NEW} parce que l'appel vient d'après le commit de la transaction métier :
 * celle-ci est terminée, il n'y a plus de transaction à rejoindre.
 *
 * <p>Toute défaillance est journalisée et avalée, exactement comme pour l'audit. Le mécanisme est
 * neuf et son mode de panne est le silence ; c'est le prix assumé pour qu'un défaut de traçabilité
 * ne puisse jamais faire échouer un visa. C'est aussi la raison pour laquelle cette table n'est pas
 * une trace probante, et pourquoi les délais par direction se lisent sur les tables
 * {@code Decision*} et non ici.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoriqueStatutWriter {

    private final HistoriqueStatutRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void ecrire(List<HistoriqueStatut> lignes) {
        if (lignes == null || lignes.isEmpty()) {
            return;
        }
        try {
            repository.saveAll(lignes);
        } catch (Exception e) {
            log.warn("Échec enregistrement de {} transition(s) de statut : {}",
                    lignes.size(), e.getMessage());
        }
    }
}
