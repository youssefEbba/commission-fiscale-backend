package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.SousTraitance;
import mr.gov.finances.sgci.domain.enums.StatutSousTraitance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("select distinct st.sousTraitantEntreprise from SousTraitance st join st.certificatCredit c "
            + "where c.entreprise.id = :titulaireEntrepriseId and st.sousTraitantEntreprise is not null")
    List<Entreprise> findDistinctSousTraitantEntreprisesForTitulaire(@Param("titulaireEntrepriseId") Long titulaireEntrepriseId);

    @Query("select distinct e from SousTraitance st join st.sousTraitantEntreprise e")
    List<Entreprise> findDistinctSousTraitantEntreprises();
}
