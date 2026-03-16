package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.SousTraitance;
import mr.gov.finances.sgci.domain.enums.StatutSousTraitance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SousTraitanceRepository extends JpaRepository<SousTraitance, Long> {

    Optional<SousTraitance> findByCertificatCreditId(Long certificatCreditId);

    List<SousTraitance> findByCertificatCreditEntrepriseId(Long entrepriseId);

    List<SousTraitance> findByCertificatCreditEntrepriseIdOrSousTraitantEntrepriseId(Long entrepriseId, Long sousTraitantEntrepriseId);

    List<SousTraitance> findByStatut(StatutSousTraitance statut);

    Optional<SousTraitance> findByCertificatCreditIdAndSousTraitantEntrepriseIdAndStatut(Long certificatCreditId, Long sousTraitantEntrepriseId, StatutSousTraitance statut);
}
