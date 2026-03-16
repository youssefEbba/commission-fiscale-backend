package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.DemandeCorrectionRejet;

@Repository
public interface DemandeCorrectionRejetRepository extends JpaRepository<DemandeCorrectionRejet, Long> {
}
