package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
