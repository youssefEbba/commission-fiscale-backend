package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.Role;

import java.util.List;
import java.util.Optional;

@Repository
public interface DecisionCorrectionRepository extends JpaRepository<DecisionCorrection, Long> {
    Optional<DecisionCorrection> findByDemandeCorrectionIdAndRole(Long demandeCorrectionId, Role role);
    List<DecisionCorrection> findByDemandeCorrectionId(Long demandeCorrectionId);

    boolean existsByDemandeCorrectionIdAndRoleAndDecision(Long demandeCorrectionId, Role role, DecisionCorrectionType decision);
}
