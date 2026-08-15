package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Signature;
import mr.gov.finances.sgci.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignatureRepository extends JpaRepository<Signature, Long> {

    Optional<Signature> findByRoleAndUtilisateur_IdAndActiveTrue(Role role, Long utilisateurId);

    Optional<Signature> findByRoleAndUtilisateurIsNullAndActiveTrue(Role role);

    List<Signature> findByRoleAndUtilisateur_Id(Role role, Long utilisateurId);

    List<Signature> findByRoleAndUtilisateurIsNull(Role role);

    List<Signature> findByRole(Role role);

    List<Signature> findByUtilisateur_Id(Long utilisateurId);

    List<Signature> findByActiveTrue();
}
