package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Avenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvenantRepository extends JpaRepository<Avenant, Long> {

    List<Avenant> findByCertificatCreditId(Long certificatCreditId);
}
