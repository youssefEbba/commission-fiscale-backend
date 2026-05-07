package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DecisionTransfertCredit;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionTransfertCreditRepository extends JpaRepository<DecisionTransfertCredit, Long> {

    List<DecisionTransfertCredit> findByTransfertCredit_Id(Long transfertCreditId);

    List<DecisionTransfertCredit> findByTransfertCredit_IdAndDecisionAndRejetTempStatus(
            Long transfertCreditId,
            DecisionCorrectionType decision,
            RejetTempStatus rejetTempStatus);
}
