package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DecisionCertificatCredit;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DecisionCertificatCreditRepository extends JpaRepository<DecisionCertificatCredit, Long> {
    Optional<DecisionCertificatCredit> findByCertificatCreditIdAndRole(Long certificatCreditId, Role role);

    List<DecisionCertificatCredit> findByCertificatCreditId(Long certificatCreditId);

    List<DecisionCertificatCredit> findByCertificatCreditIdAndDecisionAndRejetTempStatus(Long certificatCreditId,
                                                                                        DecisionCorrectionType decision,
                                                                                        RejetTempStatus rejetTempStatus);

    boolean existsByCertificatCreditIdAndRoleAndDecision(Long certificatCreditId, Role role, DecisionCorrectionType decision);
}
