package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DocumentClotureCredit;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentClotureCreditRepository extends JpaRepository<DocumentClotureCredit, Long> {

    List<DocumentClotureCredit> findByClotureCreditId(Long clotureCreditId);

    List<DocumentClotureCredit> findByClotureCreditIdAndActifTrue(Long clotureCreditId);

    Optional<DocumentClotureCredit> findByClotureCreditIdAndTypeAndActifTrue(Long clotureCreditId, TypeDocument type);
}
