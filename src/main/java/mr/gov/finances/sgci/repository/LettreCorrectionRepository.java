package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.LettreCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LettreCorrectionRepository extends JpaRepository<LettreCorrection, Long> {
}
