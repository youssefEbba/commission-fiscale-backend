package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificatCreditRepository extends JpaRepository<CertificatCredit, Long> {

    Optional<CertificatCredit> findByNumero(String numero);
    boolean existsByNumero(String numero);
    List<CertificatCredit> findByStatut(StatutCertificat statut);
    List<CertificatCredit> findByEntrepriseId(Long entrepriseId);
}
