package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.HistoriqueStatut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriqueStatutRepository extends JpaRepository<HistoriqueStatut, Long> {

    /** La chronologie d'un dossier, dans l'ordre où elle s'est produite. */
    List<HistoriqueStatut> findByEntityTypeAndEntityIdOrderByDateTransitionAsc(String entityType, Long entityId);
}
