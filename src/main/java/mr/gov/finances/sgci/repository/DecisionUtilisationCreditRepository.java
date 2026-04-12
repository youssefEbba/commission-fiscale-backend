package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DecisionUtilisationCredit;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionUtilisationCreditRepository extends JpaRepository<DecisionUtilisationCredit, Long> {
    List<DecisionUtilisationCredit> findByUtilisationCreditId(Long utilisationCreditId);

    List<DecisionUtilisationCredit> findByUtilisationCreditIdAndDecisionAndRejetTempStatus(Long utilisationCreditId,
                                                                                          DecisionCorrectionType decision,
                                                                                          RejetTempStatus rejetTempStatus);

    boolean existsByUtilisationCreditIdAndRoleAndDecision(Long utilisationCreditId, Role role, DecisionCorrectionType decision);

    boolean existsByUtilisationCreditIdAndRoleAndDecisionAndRejetTempStatus(
            Long utilisationCreditId,
            Role role,
            DecisionCorrectionType decision,
            RejetTempStatus rejetTempStatus);
}
