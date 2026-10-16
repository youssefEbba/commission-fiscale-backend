package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.ParametreApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParametreApplicationRepository extends JpaRepository<ParametreApplication, Long> {

    Optional<ParametreApplication> findByCle(String cle);

    boolean existsByCle(String cle);

    List<ParametreApplication> findAllByOrderByCleAsc();
}
