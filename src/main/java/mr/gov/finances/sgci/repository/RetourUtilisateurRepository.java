package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.RetourUtilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RetourUtilisateurRepository extends JpaRepository<RetourUtilisateur, Long> {

    List<RetourUtilisateur> findByDateCreationBetweenOrderByDateCreationDesc(Instant from, Instant to);

    /** Dernier avis d'un utilisateur depuis un instant donné : sert à écarter un envoi répété. */
    Optional<RetourUtilisateur> findFirstByUtilisateurIdAndDateCreationAfterOrderByDateCreationDesc(Long utilisateurId, Instant depuis);
}
