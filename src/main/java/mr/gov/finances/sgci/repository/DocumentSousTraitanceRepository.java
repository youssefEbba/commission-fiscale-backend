package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DocumentSousTraitance;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentSousTraitanceRepository extends JpaRepository<DocumentSousTraitance, Long> {

    List<DocumentSousTraitance> findBySousTraitanceId(Long sousTraitanceId);

    List<DocumentSousTraitance> findBySousTraitanceIdAndActifTrue(Long sousTraitanceId);

    Optional<DocumentSousTraitance> findBySousTraitanceIdAndTypeAndActifTrue(Long sousTraitanceId, TypeDocument type);
}
