package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;

import java.util.List;

@Repository
public interface DecisionCorrectionRepository extends JpaRepository<DecisionCorrection, Long> {
    List<DecisionCorrection> findByDemandeCorrectionId(Long demandeCorrectionId);

    List<DecisionCorrection> findByDemandeCorrectionIdAndDecisionAndRejetTempStatus(Long demandeCorrectionId, DecisionCorrectionType decision, RejetTempStatus rejetTempStatus);

    boolean existsByDemandeCorrectionIdAndRoleAndDecision(Long demandeCorrectionId, Role role, DecisionCorrectionType decision);

    /** Au moins un rejet temporaire encore ouvert pour ce rôle sur la demande (plusieurs rejets isolés possibles). */
    boolean existsByDemandeCorrectionIdAndRoleAndDecisionAndRejetTempStatus(
            Long demandeCorrectionId,
            Role role,
            DecisionCorrectionType decision,
            RejetTempStatus rejetTempStatus);
}
