package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConventionRepository extends JpaRepository<Convention, Long> {
    Optional<Convention> findByReference(String reference);
    List<Convention> findByStatut(StatutConvention statut);

    List<Convention> findByBailleurId(Long bailleurId);

    List<Convention> findAllByAutoriteContractanteId(Long autoriteContractanteId);

    List<Convention> findAllByAutoriteContractanteIdAndStatut(Long autoriteContractanteId, StatutConvention statut);

    @Query("select distinct m.convention from Marche m join m.delegues md where md.delegue.id = :delegueId")
    List<Convention> findAllByDelegueId(@Param("delegueId") Long delegueId);

    @Query("select distinct m.convention from Marche m join m.delegues md where md.delegue.id = :delegueId and m.convention.statut = :statut")
    List<Convention> findAllByDelegueIdAndStatut(@Param("delegueId") Long delegueId, @Param("statut") StatutConvention statut);

    @Query("select count(m) > 0 from Marche m join m.delegues md where md.delegue.id = :delegueId and m.convention.id = :conventionId")
    boolean existsAccessByDelegue(@Param("delegueId") Long delegueId, @Param("conventionId") Long conventionId);

    @Query("SELECT c FROM Convention c WHERE LOWER(c.reference) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(c.intitule) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR (c.projectReference IS NOT NULL AND LOWER(c.projectReference) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "OR (c.bailleur IS NOT NULL AND LOWER(c.bailleur.nom) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Convention> searchByReferenceIntituleOrProject(@Param("q") String q);
}
