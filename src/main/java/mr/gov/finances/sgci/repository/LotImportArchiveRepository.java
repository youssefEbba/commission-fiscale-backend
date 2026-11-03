package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.LotImportArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LotImportArchiveRepository extends JpaRepository<LotImportArchive, Long> {

    Optional<LotImportArchive> findByEmpreinteSha256(String empreinteSha256);

    List<LotImportArchive> findAllByOrderByDateAnalyseDesc();
}
