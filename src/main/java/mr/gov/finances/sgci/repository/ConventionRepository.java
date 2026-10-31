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

    @Query("select distinct c from Convention c left join c.bailleurs b where b.id = :bailleurId or c.bailleur.id = :bailleurId")
    List<Convention> findByBailleurId(@Param("bailleurId") Long bailleurId);

    List<Convention> findAllByAutoriteContractanteId(Long autoriteContractanteId);

    /**
     * Le périmètre d'une autorité contractante : ce qu'elle a créé ou ce dont elle est titulaire.
     *
     * <p>Les deux, et non la seule créatrice : 210 des 215 conventions en base n'ont aucune AC
     * créatrice renseignée, le champ étant postérieur. Filtrer sur elle seule aurait vidé la liste
     * de toutes les autorités.
     */
    @Query("select distinct c from Convention c left join c.autorites a where c.autoriteContractante.id = :acId "
            + "or c.creeParAutoriteContractante.id = :acId or a.id = :acId")
    List<Convention> findAllPourAutorite(@Param("acId") Long acId);

    @Query("select distinct c from Convention c left join c.autorites a where (c.autoriteContractante.id = :acId "
            + "or c.creeParAutoriteContractante.id = :acId or a.id = :acId) and c.statut = :statut")
    List<Convention> findAllPourAutoriteAndStatut(@Param("acId") Long acId,
                                                  @Param("statut") StatutConvention statut);

    List<Convention> findByActifIsNull();

    List<Convention> findAllByAutoriteContractanteIdAndStatut(Long autoriteContractanteId, StatutConvention statut);

    @Query("select distinct c from Marche m join m.delegues md join m.conventions c where md.delegue.id = :delegueId")
    List<Convention> findAllByDelegueId(@Param("delegueId") Long delegueId);

    @Query("select distinct c from Marche m join m.delegues md join m.conventions c where md.delegue.id = :delegueId and c.statut = :statut")
    List<Convention> findAllByDelegueIdAndStatut(@Param("delegueId") Long delegueId, @Param("statut") StatutConvention statut);

    @Query("select count(m) > 0 from Marche m join m.delegues md join m.conventions c where md.delegue.id = :delegueId and c.id = :conventionId")
    boolean existsAccessByDelegue(@Param("delegueId") Long delegueId, @Param("conventionId") Long conventionId);

    /**
     * Recherche par référence, intitulé, référence projet ou nom du bailleur.
     *
     * <p>La jointure sur le bailleur est <b>externe et explicite</b>. Écrite {@code c.bailleur.nom}
     * dans la clause where, elle était interne : toute convention sans bailleur disparaissait des
     * résultats, même quand sa référence correspondait exactement.
     */
    @Query("SELECT DISTINCT c FROM Convention c LEFT JOIN c.bailleurs b "
            + "WHERE LOWER(c.reference) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(c.intitule) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR (c.projectReference IS NOT NULL AND LOWER(c.projectReference) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "OR (b IS NOT NULL AND LOWER(b.nom) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Convention> searchByReferenceIntituleOrProject(@Param("q") String q);
}
