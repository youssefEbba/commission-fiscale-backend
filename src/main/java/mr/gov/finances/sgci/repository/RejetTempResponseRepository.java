package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.RejetTempResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RejetTempResponseRepository extends JpaRepository<RejetTempResponse, Long> {

    List<RejetTempResponse> findByDecisionUtilisationCredit_IdOrderByCreatedAtAsc(Long decisionUtilisationCreditId);
}
