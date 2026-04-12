package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DecisionCertificatCredit;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionCertificatCreditRepository extends JpaRepository<DecisionCertificatCredit, Long> {
    List<DecisionCertificatCredit> findByCertificatCreditId(Long certificatCreditId);

    List<DecisionCertificatCredit> findByCertificatCreditIdAndDecisionAndRejetTempStatus(Long certificatCreditId,
                                                                                        DecisionCorrectionType decision,
                                                                                        RejetTempStatus rejetTempStatus);

    boolean existsByCertificatCreditIdAndRoleAndDecision(Long certificatCreditId, Role role, DecisionCorrectionType decision);

    boolean existsByCertificatCreditIdAndRoleAndDecisionAndRejetTempStatus(
            Long certificatCreditId,
            Role role,
            DecisionCorrectionType decision,
            RejetTempStatus rejetTempStatus);
}
