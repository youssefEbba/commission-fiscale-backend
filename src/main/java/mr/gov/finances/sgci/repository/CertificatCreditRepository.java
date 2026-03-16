package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificatCreditRepository extends JpaRepository<CertificatCredit, Long> {

    Optional<CertificatCredit> findByNumero(String numero);
    boolean existsByNumero(String numero);
    boolean existsByDemandeCorrectionId(Long demandeCorrectionId);
    Optional<CertificatCredit> findFirstByDemandeCorrectionId(Long demandeCorrectionId);
    List<CertificatCredit> findByStatut(StatutCertificat statut);
    List<CertificatCredit> findByEntrepriseId(Long entrepriseId);

    Optional<CertificatCredit> findFirstByEntrepriseIdAndStatutOrderByIdDesc(Long entrepriseId, StatutCertificat statut);

    @Query("select distinct c from CertificatCredit c join c.demandeCorrection dc where dc.autoriteContractante.id = :autoriteContractanteId")
    List<CertificatCredit> findAllByAutoriteContractanteId(@Param("autoriteContractanteId") Long autoriteContractanteId);

    @Query("select distinct c from CertificatCredit c join c.demandeCorrection dc join dc.marche m join m.delegues md where md.delegue.id = :delegueId")
    List<CertificatCredit> findAllByDelegueId(@Param("delegueId") Long delegueId);

    @Query("select count(c) > 0 from CertificatCredit c join c.demandeCorrection dc join dc.marche m join m.delegues md where md.delegue.id = :delegueId and c.id = :certificatId")
    boolean existsAccessByDelegue(@Param("delegueId") Long delegueId, @Param("certificatId") Long certificatId);
}
