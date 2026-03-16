package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, Long> {
    List<DocumentRequirement> findByProcessusOrderByOrdreAffichageAsc(ProcessusDocument processus);

    boolean existsByProcessusAndTypeDocument(ProcessusDocument processus, TypeDocument typeDocument);
}
