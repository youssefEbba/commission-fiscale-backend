package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.enums.StatutDemande;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeCorrectionRepository extends JpaRepository<DemandeCorrection, Long> {

    Optional<DemandeCorrection> findByNumero(String numero);
    boolean existsByNumero(String numero);

    /** Vrai si l’entreprise a déjà une demande dont le numéro commence par {@code prefix} (seed démo idempotent). */
    boolean existsByEntreprise_IdAndNumeroStartingWith(Long entrepriseId, String prefix);

    /** Plus récentes en tête (listes UI, sélecteurs) */
    List<DemandeCorrection> findByStatutOrderByDateDepotDescIdDesc(StatutDemande statut);

    List<DemandeCorrection> findByAutoriteContractanteIdOrderByDateDepotDescIdDesc(Long autoriteContractanteId);

    List<DemandeCorrection> findByAutoriteContractanteIdAndStatutOrderByDateDepotDescIdDesc(
            Long autoriteContractanteId, StatutDemande statut);

    List<DemandeCorrection> findByEntrepriseIdOrderByDateDepotDescIdDesc(Long entrepriseId);

    List<DemandeCorrection> findAllByOrderByDateDepotDescIdDesc();

    @Query("select distinct dc from DemandeCorrection dc join dc.marche m join m.delegues md where md.delegue.id = :delegueId order by dc.dateDepot desc, dc.id desc")
    List<DemandeCorrection> findByDelegueId(@Param("delegueId") Long delegueId);

    @Query("select count(dc) > 0 from DemandeCorrection dc join dc.marche m join m.delegues md where md.delegue.id = :delegueId and dc.id = :demandeId")
    boolean existsAccessByDelegue(@Param("delegueId") Long delegueId, @Param("demandeId") Long demandeId);
}
