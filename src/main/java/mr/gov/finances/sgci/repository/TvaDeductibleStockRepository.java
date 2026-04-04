package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.TvaDeductibleStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TvaDeductibleStockRepository extends JpaRepository<TvaDeductibleStock, Long> {
    List<TvaDeductibleStock> findByCertificatCreditIdOrderByDateCreationAsc(Long certificatCreditId);
}
