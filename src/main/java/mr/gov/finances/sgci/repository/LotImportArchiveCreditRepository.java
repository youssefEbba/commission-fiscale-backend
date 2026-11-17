package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.LotImportArchiveCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LotImportArchiveCreditRepository extends JpaRepository<LotImportArchiveCredit, Long> {

    List<LotImportArchiveCredit> findByLotIdOrderByOrdreAsc(Long lotId);

    /** Crédits de lot qui ont produit, ou retrouvé, ce certificat (tous lots confondus). */
    List<LotImportArchiveCredit> findByCertificatId(Long certificatId);

    List<LotImportArchiveCredit> findByMarcheId(Long marcheId);
}
