package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DemandeResetPassword;
import mr.gov.finances.sgci.domain.enums.StatutDemandeResetPassword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeResetPasswordRepository extends JpaRepository<DemandeResetPassword, Long> {

    List<DemandeResetPassword> findByStatutOrderByDateCreationDesc(StatutDemandeResetPassword statut);

    List<DemandeResetPassword> findAllByOrderByDateCreationDesc();

    boolean existsByUtilisateurIdAndStatut(Long utilisateurId, StatutDemandeResetPassword statut);

    Optional<DemandeResetPassword> findByUtilisateurIdAndStatut(Long utilisateurId, StatutDemandeResetPassword statut);
}
