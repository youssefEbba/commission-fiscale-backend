package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DemandeExplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeExplicationRepository extends JpaRepository<DemandeExplication, Long> {

    List<DemandeExplication> findByDemandeCorrectionIdOrderByDateOuvertureDesc(Long demandeCorrectionId);

    List<DemandeExplication> findByCertificatCreditIdOrderByDateOuvertureDesc(Long certificatCreditId);

    List<DemandeExplication> findByUtilisationCreditIdOrderByDateOuvertureDesc(Long utilisationCreditId);

    @Query("SELECT DISTINCT e FROM DemandeExplication e "
            + "LEFT JOIN FETCH e.messages m "
            + "LEFT JOIN FETCH m.auteur "
            + "LEFT JOIN FETCH e.auteur "
            + "WHERE e.id = :id")
    Optional<DemandeExplication> findByIdWithMessages(@Param("id") Long id);

    @Query("SELECT DISTINCT e FROM DemandeExplication e "
            + "LEFT JOIN FETCH e.messages m "
            + "LEFT JOIN FETCH m.auteur "
            + "LEFT JOIN FETCH e.auteur "
            + "WHERE e.demandeCorrection.id = :demandeCorrectionId "
            + "ORDER BY e.dateOuverture DESC")
    List<DemandeExplication> findByDemandeCorrectionIdWithMessages(@Param("demandeCorrectionId") Long demandeCorrectionId);

    @Query("SELECT DISTINCT e FROM DemandeExplication e "
            + "LEFT JOIN FETCH e.messages m "
            + "LEFT JOIN FETCH m.auteur "
            + "LEFT JOIN FETCH e.auteur "
            + "WHERE e.certificatCredit.id = :certificatCreditId "
            + "ORDER BY e.dateOuverture DESC")
    List<DemandeExplication> findByCertificatCreditIdWithMessages(@Param("certificatCreditId") Long certificatCreditId);

    @Query("SELECT DISTINCT e FROM DemandeExplication e "
            + "LEFT JOIN FETCH e.messages m "
            + "LEFT JOIN FETCH m.auteur "
            + "LEFT JOIN FETCH e.auteur "
            + "WHERE e.utilisationCredit.id = :utilisationCreditId "
            + "ORDER BY e.dateOuverture DESC")
    List<DemandeExplication> findByUtilisationCreditIdWithMessages(@Param("utilisationCreditId") Long utilisationCreditId);
}
