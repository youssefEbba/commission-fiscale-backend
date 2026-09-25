package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.QuittanceDgi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuittanceDgiRepository extends JpaRepository<QuittanceDgi, Long> {

    /** Quittance rattachée à une utilisation ; au plus une, le dépôt étant idempotent. */
    Optional<QuittanceDgi> findByUtilisationCreditId(Long utilisationCreditId);
}
