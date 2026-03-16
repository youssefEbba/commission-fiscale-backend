package mr.gov.finances.sgci.repository;

import java.util.List;

import mr.gov.finances.sgci.domain.entity.DocumentProjet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentProjetRepository extends JpaRepository<DocumentProjet, Long> {
    List<DocumentProjet> findByReferentielProjetId(Long referentielProjetId);
}
