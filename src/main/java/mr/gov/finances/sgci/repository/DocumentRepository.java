package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.enums.TypeDocument;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByDemandeCorrectionId(Long demandeCorrectionId);

    List<Document> findByDemandeCorrectionIdAndActifTrue(Long demandeCorrectionId);
    Optional<Document> findByDemandeCorrectionIdAndTypeAndActifTrue(Long demandeCorrectionId, TypeDocument type);

    Optional<Document> findTopByDemandeCorrection_IdAndTypeOrderByVersionDesc(Long demandeCorrectionId, TypeDocument type);
}
