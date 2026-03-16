package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Devise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviseRepository extends JpaRepository<Devise, Long> {
    Optional<Devise> findByCode(String code);
    boolean existsByCode(String code);
}
