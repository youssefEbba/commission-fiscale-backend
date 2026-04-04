package mr.gov.finances.sgci.repository;

import java.util.List;

import mr.gov.finances.sgci.domain.entity.ReferentielProjet;
import mr.gov.finances.sgci.domain.enums.StatutReferentielProjet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReferentielProjetRepository extends JpaRepository<ReferentielProjet, Long> {
    List<ReferentielProjet> findByStatut(StatutReferentielProjet statut);
    List<ReferentielProjet> findByAutoriteContractanteId(Long autoriteContractanteId);

    List<ReferentielProjet> findByConventionId(Long conventionId);
}
