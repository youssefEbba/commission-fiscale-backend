package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DocumentAvenant;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentAvenantRepository extends JpaRepository<DocumentAvenant, Long> {

    List<DocumentAvenant> findByAvenantId(Long avenantId);

    List<DocumentAvenant> findByAvenantIdAndActifTrue(Long avenantId);

    Optional<DocumentAvenant> findByAvenantIdAndTypeAndActifTrue(Long avenantId, TypeDocument type);
}
