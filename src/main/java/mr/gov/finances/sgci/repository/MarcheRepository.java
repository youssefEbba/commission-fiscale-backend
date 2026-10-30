package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Marche;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarcheRepository extends JpaRepository<Marche, Long> {

    Optional<Marche> findByDemandeCorrectionId(Long demandeCorrectionId);

    /** Les marchés que porte l'autorité (et non plus ceux de ses conventions, qui en relient d'autres). */
    List<Marche> findAllByAutoriteContractanteId(Long autoriteContractanteId);

    /** L'autorité a-t-elle déjà un marché sous cette convention ? Empêche de l'en délier. */
    @Query("select count(m) > 0 from Marche m join m.conventions c where c.id = :conventionId "
            + "and m.autoriteContractante.id = :acId")
    boolean existsByConventionAndAutorite(@Param("conventionId") Long conventionId, @Param("acId") Long acId);

    @Query("select distinct m from Marche m join m.delegues md where m.autoriteContractante.id = :autoriteContractanteId and md.delegue.id = :delegueId")
    List<Marche> findAllByConventionAutoriteContractanteIdAndDelegueId(@Param("autoriteContractanteId") Long autoriteContractanteId,
                                                                      @Param("delegueId") Long delegueId);

    List<Marche> findAllByDemandeCorrectionAutoriteContractanteId(Long autoriteContractanteId);

    @Query("select distinct m from Marche m join m.delegues md where m.demandeCorrection.autoriteContractante.id = :autoriteContractanteId and md.delegue.id = :delegueId")
    List<Marche> findAllByDemandeCorrectionAutoriteContractanteIdAndDelegueId(@Param("autoriteContractanteId") Long autoriteContractanteId,
                                                                             @Param("delegueId") Long delegueId);

    /** Marchés de l'AC auxquels le délégué est rattaché (via table marche_delegue). */
    @Query("select distinct m from Marche m join m.delegues md where md.delegue.id = :delegueId and m.autoriteContractante.id = :acId")
    List<Marche> findAllByDelegueIdAndAutoriteContractanteId(@Param("delegueId") Long delegueId, @Param("acId") Long acId);

    @Query("SELECT m FROM Marche m WHERE m.autoriteContractante.id = :acId AND ("
            + "LOWER(m.numeroMarche) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR (m.intitule IS NOT NULL AND LOWER(m.intitule) LIKE LOWER(CONCAT('%', :q, '%'))))")
    List<Marche> searchByAcAndNumeroOrIntitule(@Param("acId") Long acId, @Param("q") String q);

    @Query("SELECT m FROM Marche m WHERE LOWER(m.numeroMarche) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR (m.intitule IS NOT NULL AND LOWER(m.intitule) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Marche> searchByNumeroOrIntitule(@Param("q") String q);

    @Query("SELECT m FROM Marche m WHERE LOWER(m.numeroMarche) = LOWER(:numeroMarche)")
    Optional<Marche> findFirstByNumeroMarcheIgnoreCase(@Param("numeroMarche") String numeroMarche);
}
