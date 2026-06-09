package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, Long> {
    List<DocumentRequirement> findByProcessusOrderByOrdreAffichageAsc(ProcessusDocument processus);

    boolean existsByProcessusAndCodeDocument(ProcessusDocument processus, String codeDocument);

    Optional<DocumentRequirement> findByProcessusAndCodeDocument(ProcessusDocument processus, String codeDocument);
}
