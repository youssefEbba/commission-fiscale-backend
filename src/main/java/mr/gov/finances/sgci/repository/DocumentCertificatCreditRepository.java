package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DocumentCertificatCredit;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentCertificatCreditRepository extends JpaRepository<DocumentCertificatCredit, Long> {

    List<DocumentCertificatCredit> findByCertificatCreditId(Long certificatCreditId);

    List<DocumentCertificatCredit> findByCertificatCreditIdAndActifTrue(Long certificatCreditId);

    Optional<DocumentCertificatCredit> findByCertificatCreditIdAndTypeAndActifTrue(Long certificatCreditId, TypeDocument type);
}
