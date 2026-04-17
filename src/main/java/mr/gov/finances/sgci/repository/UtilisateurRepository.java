package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByUsername(String username);
    boolean existsByUsername(String username);

    List<Utilisateur> findByEntrepriseId(Long entrepriseId);

    List<Utilisateur> findByAutoriteContractanteId(Long autoriteContractanteId);

    List<Utilisateur> findByAutoriteContractanteIdAndRoleIn(Long autoriteContractanteId, List<Role> roles);

    List<Utilisateur> findByRole(Role role);

    List<Utilisateur> findAllByOrderByIdDesc();

    List<Utilisateur> findByActifFalseOrderByIdDesc();

    boolean existsByEmailAndIdNot(String email, Long id);
}
