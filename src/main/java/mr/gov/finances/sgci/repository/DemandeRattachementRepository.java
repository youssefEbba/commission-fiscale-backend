package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DemandeRattachement;
import mr.gov.finances.sgci.domain.enums.StatutDemandeRattachement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DemandeRattachementRepository extends JpaRepository<DemandeRattachement, Long> {

    List<DemandeRattachement> findByStatutOrderByDateCreationDesc(StatutDemandeRattachement statut);

    List<DemandeRattachement> findAllByOrderByDateCreationDesc();

    Optional<DemandeRattachement> findByReference(String reference);

    /**
     * Sert à refuser un second dépôt tant que le premier n'est pas tranché.
     *
     * <p>Sans ce contrôle, une personne impatiente empilerait les demandes et le Président
     * trancherait plusieurs fois le même cas — en créant autant de comptes.
     */
    boolean existsByUsernameAndStatut(String username, StatutDemandeRattachement statut);
}
