package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.ReclamationDemandeCorrection;
import mr.gov.finances.sgci.domain.enums.StatutReclamationCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReclamationDemandeCorrectionRepository extends JpaRepository<ReclamationDemandeCorrection, Long> {

    boolean existsByDemandeCorrection_IdAndStatut(Long demandeCorrectionId, StatutReclamationCorrection statut);

    List<ReclamationDemandeCorrection> findByDemandeCorrection_IdOrderByDateCreationDesc(Long demandeCorrectionId);

    Optional<ReclamationDemandeCorrection> findByIdAndDemandeCorrection_Id(Long id, Long demandeCorrectionId);
}
