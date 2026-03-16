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
    List<DemandeCorrection> findByStatut(StatutDemande statut);
    List<DemandeCorrection> findByAutoriteContractanteId(Long autoriteContractanteId);
    List<DemandeCorrection> findByEntrepriseId(Long entrepriseId);

    @Query("select distinct dc from DemandeCorrection dc join dc.marche m join m.delegues md where md.delegue.id = :delegueId")
    List<DemandeCorrection> findByDelegueId(@Param("delegueId") Long delegueId);

    @Query("select count(dc) > 0 from DemandeCorrection dc join dc.marche m join m.delegues md where md.delegue.id = :delegueId and dc.id = :demandeId")
    boolean existsAccessByDelegue(@Param("delegueId") Long delegueId, @Param("demandeId") Long demandeId);
}
