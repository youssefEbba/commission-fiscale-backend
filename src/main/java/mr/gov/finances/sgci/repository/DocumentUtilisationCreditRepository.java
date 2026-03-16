package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentUtilisationCreditRepository extends JpaRepository<DocumentUtilisationCredit, Long> {

    List<DocumentUtilisationCredit> findByUtilisationCreditId(Long utilisationCreditId);

    List<DocumentUtilisationCredit> findByUtilisationCreditIdAndActifTrue(Long utilisationCreditId);

    Optional<DocumentUtilisationCredit> findByUtilisationCreditIdAndTypeAndActifTrue(Long utilisationCreditId, TypeDocument type);
}
