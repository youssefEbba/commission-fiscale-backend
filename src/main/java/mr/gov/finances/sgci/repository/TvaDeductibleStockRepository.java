package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.TvaDeductibleStock;
import mr.gov.finances.sgci.domain.enums.TvaDeductibleStockSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TvaDeductibleStockRepository extends JpaRepository<TvaDeductibleStock, Long> {
    List<TvaDeductibleStock> findByCertificatCreditIdOrderByDateCreationAsc(Long certificatCreditId);

    boolean existsByCertificatCreditIdAndSource(Long certificatCreditId, TvaDeductibleStockSource source);

    /**
     * Données historiques : avant la colonne {@code source}, le transfert seul créait des lignes sans utilisation douanière.
     */
    boolean existsByCertificatCreditIdAndUtilisationDouaneIsNull(Long certificatCreditId);
}
