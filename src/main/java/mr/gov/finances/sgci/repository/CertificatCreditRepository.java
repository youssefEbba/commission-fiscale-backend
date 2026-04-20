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

    /** Certificats non annulés pour cette demande (unicité mise en place). */
    long countByDemandeCorrectionIdAndStatutNot(Long demandeCorrectionId, StatutCertificat statut);

    /** Autres certificats non annulés pour la même demande (exclut le certificat en cours d’édition / soumission). */
    long countByDemandeCorrectionIdAndStatutNotAndIdNot(Long demandeCorrectionId, StatutCertificat statut, Long excludeCertificatId);
    List<CertificatCredit> findByStatutOrderByDateEmissionDescIdDesc(StatutCertificat statut);

    List<CertificatCredit> findByEntrepriseIdOrderByDateEmissionDescIdDesc(Long entrepriseId);

    List<CertificatCredit> findAllByOrderByDateEmissionDescIdDesc();

    Optional<CertificatCredit> findFirstByEntrepriseIdAndStatutOrderByIdDesc(Long entrepriseId, StatutCertificat statut);

    @Query("select distinct c from CertificatCredit c join c.demandeCorrection dc where dc.autoriteContractante.id = :autoriteContractanteId order by c.dateEmission desc, c.id desc")
    List<CertificatCredit> findAllByAutoriteContractanteId(@Param("autoriteContractanteId") Long autoriteContractanteId);

    @Query("select distinct c from CertificatCredit c join c.demandeCorrection dc join dc.marche m join m.delegues md where md.delegue.id = :delegueId order by c.dateEmission desc, c.id desc")
    List<CertificatCredit> findAllByDelegueId(@Param("delegueId") Long delegueId);

    @Query("select count(c) > 0 from CertificatCredit c join c.demandeCorrection dc join dc.marche m join m.delegues md where md.delegue.id = :delegueId and c.id = :certificatId")
    boolean existsAccessByDelegue(@Param("delegueId") Long delegueId, @Param("certificatId") Long certificatId);
}
