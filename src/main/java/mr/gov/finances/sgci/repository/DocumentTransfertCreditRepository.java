package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DocumentTransfertCredit;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentTransfertCreditRepository extends JpaRepository<DocumentTransfertCredit, Long> {

    List<DocumentTransfertCredit> findByTransfertCreditId(Long transfertCreditId);

    List<DocumentTransfertCredit> findByTransfertCreditIdAndActifTrue(Long transfertCreditId);

    Optional<DocumentTransfertCredit> findByTransfertCreditIdAndTypeAndActifTrue(Long transfertCreditId, TypeDocument type);
}
