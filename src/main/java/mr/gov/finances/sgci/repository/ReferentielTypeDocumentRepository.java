package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.ReferentielTypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferentielTypeDocumentRepository extends JpaRepository<ReferentielTypeDocument, String> {

    List<ReferentielTypeDocument> findByActifTrueOrderByCodeAsc();

    List<ReferentielTypeDocument> findAllByOrderByCodeAsc();
}
