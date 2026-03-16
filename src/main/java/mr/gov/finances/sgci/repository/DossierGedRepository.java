package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DossierGed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DossierGedRepository extends JpaRepository<DossierGed, Long> {

    Optional<DossierGed> findByDemandeCorrectionId(Long demandeCorrectionId);

    Optional<DossierGed> findByCertificatCreditId(Long certificatCreditId);

    @Query("select distinct d from DossierGed d join d.demandeCorrection dc where dc.autoriteContractante.id = :autoriteContractanteId")
    List<DossierGed> findAllByAutoriteContractanteId(@Param("autoriteContractanteId") Long autoriteContractanteId);

    @Query("select distinct d from DossierGed d join d.demandeCorrection dc join dc.marche m join m.delegues md where md.delegue.id = :delegueId")
    List<DossierGed> findAllByDelegueId(@Param("delegueId") Long delegueId);

    List<DossierGed> findByEntrepriseId(Long entrepriseId);
}
