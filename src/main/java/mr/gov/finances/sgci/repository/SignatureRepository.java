package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Signature;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeEmpreinte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignatureRepository extends JpaRepository<Signature, Long> {

    // ── Résolution de l'empreinte active ────────────────────────────────────────────────────────
    // Volontairement des List et non des Optional : l'unicité de l'empreinte active n'est garantie
    // qu'applicativement (MySQL n'a pas d'index partiel, et un index sur (type, role, utilisateur,
    // active) interdirait aussi les lignes inactives, donc tout l'historique de versionnement).
    // Deux uploads concurrents peuvent laisser deux lignes actives ; un Optional ferait alors
    // tomber la lecture en 500. Avec une List triée, le système reste debout et rend la plus
    // récente. Tolérer l'anomalie vaut mieux que de prétendre l'interdire.

    List<Signature> findByTypeAndRoleAndUtilisateur_IdAndActiveTrueOrderByVersionDescIdDesc(
            TypeEmpreinte type, Role role, Long utilisateurId);

    List<Signature> findByTypeAndRoleAndUtilisateurIsNullAndActiveTrueOrderByVersionDescIdDesc(
            TypeEmpreinte type, Role role);

    // ── Désactivation de l'existant, scopée par type ────────────────────────────────────────────
    // Sans le scope de type, téléverser un cachet désactiverait la signature du même (role, user).

    List<Signature> findByTypeAndRoleAndUtilisateur_Id(TypeEmpreinte type, Role role, Long utilisateurId);

    List<Signature> findByTypeAndRoleAndUtilisateurIsNull(TypeEmpreinte type, Role role);

    // ── Listes ─────────────────────────────────────────────────────────────────────────────────

    List<Signature> findByRoleAndUtilisateur_Id(Role role, Long utilisateurId);

    List<Signature> findByRole(Role role);

    List<Signature> findByUtilisateur_Id(Long utilisateurId);

    /** Rattrapage au démarrage des lignes antérieures à l'introduction du cachet. */
    List<Signature> findByTypeIsNull();
}
