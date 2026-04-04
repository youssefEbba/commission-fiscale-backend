package mr.gov.finances.sgci.repository;

import java.util.List;
import java.util.Optional;

import mr.gov.finances.sgci.domain.entity.DocumentConvention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentConventionRepository extends JpaRepository<DocumentConvention, Long> {
    List<DocumentConvention> findByConventionId(Long conventionId);
    Optional<DocumentConvention> findByIdAndConventionId(Long id, Long conventionId);
}
