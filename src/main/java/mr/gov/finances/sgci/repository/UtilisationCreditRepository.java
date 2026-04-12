package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.UtilisationCredit;

import java.util.List;

@Repository
public interface UtilisationCreditRepository extends JpaRepository<UtilisationCredit, Long> {

    List<UtilisationCredit> findByCertificatCreditId(Long certificatCreditId);

    List<UtilisationCredit> findByEntrepriseId(Long entrepriseId);

    /** Toutes les utilisations sur les certificats dont l’entreprise titulaire est {@code entrepriseId}. */
    List<UtilisationCredit> findByCertificatCredit_Entreprise_Id(Long entrepriseId);

    @Query("select u from UtilisationCredit u join u.certificatCredit c join c.demandeCorrection dc "
            + "where dc.autoriteContractante.id = :autoriteContractanteId")
    List<UtilisationCredit> findAllByAutoriteContractanteId(@Param("autoriteContractanteId") Long autoriteContractanteId);

    @Query("select u from UtilisationCredit u join u.certificatCredit c join c.demandeCorrection dc join dc.marche m join m.delegues md "
            + "where md.delegue.id = :delegueId")
    List<UtilisationCredit> findAllByDelegueId(@Param("delegueId") Long delegueId);
}
