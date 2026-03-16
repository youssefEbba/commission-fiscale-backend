package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Avenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvenantRepository extends JpaRepository<Avenant, Long> {
}
