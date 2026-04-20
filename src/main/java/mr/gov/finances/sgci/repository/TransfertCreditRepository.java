package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransfertCreditRepository extends JpaRepository<TransfertCredit, Long> {

    List<TransfertCredit> findByCertificatCreditId(Long certificatCreditId);

    Optional<TransfertCredit> findFirstByCertificatCreditIdOrderByIdDesc(Long certificatCreditId);

    List<TransfertCredit> findByCertificatCreditEntrepriseId(Long entrepriseId);

    List<TransfertCredit> findByStatut(StatutTransfert statut);

    boolean existsByCertificatCreditIdAndStatut(Long certificatCreditId, StatutTransfert statut);
}
