package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.web.dto.UtilisateurDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Les personnes rattachées à une entreprise ou à une autorité contractante, vues et administrées
 * depuis la fiche de l'entité.
 *
 * <p>Service distinct de {@link DelegueService} plutôt que généralisation de celui-ci. La raison est
 * dans le code de l'autre : chacune de ses méthodes commence par {@code requireAutoriteContractante}
 * qui exige que <em>l'appelant</em> soit l'autorité concernée, et refuse l'impersonation. Le desserrer
 * pour y faire entrer le Président aurait touché la garde de toutes ses méthodes, y compris celles que
 * l'AC continue d'utiliser. Ici, l'entité est un paramètre, et c'est la permission qui autorise.
 *
 * <p>Il ne duplique aucune règle métier : il liste, crée et active, sur les mêmes dépôts.
 */
@Service
@RequiredArgsConstructor
public class ComptesEntiteService {

    private final UtilisateurRepository utilisateurRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final AutoriteContractanteRepository autoriteContractanteRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * Tous les comptes rattachés, quel que soit leur rôle.
     *
     * <p>Contrairement à la vue de l'AC, qui ne montre que les délégués : le Président doit voir le
     * titulaire du compte principal aussi, sans quoi il ne saurait pas qui administre réellement
     * l'entité.
     */
    @Transactional(readOnly = true)
    public List<UtilisateurDto> lister(TypeEntiteRattachement type, Long entiteId) {
        assertEntiteExiste(type, entiteId);
        List<Utilisateur> comptes = type == TypeEntiteRattachement.ENTREPRISE
                ? utilisateurRepository.findByEntrepriseId(entiteId)
                : utilisateurRepository.findByAutoriteContractanteId(entiteId);
        return comptes.stream().map(this::toDto).toList();
    }

    @Transactional
    public UtilisateurDto creer(TypeEntiteRattachement type, Long entiteId, String username,
                                String motDePasse, Role role, String nomComplet, String email) {
        assertEntiteExiste(type, entiteId);
        assertRoleCoherent(type, role);
        if (username == null || username.isBlank() || motDePasse == null || motDePasse.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Nom d'utilisateur et mot de passe sont obligatoires");
        }
        if (utilisateurRepository.existsByUsername(username.trim())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Ce nom d'utilisateur est déjà utilisé");
        }

        Utilisateur u = Utilisateur.builder()
                .username(username.trim())
                .passwordHash(passwordEncoder.encode(motDePasse))
                .role(role)
                .nomComplet(nomComplet)
                .email(email)
                .actif(true)
                .build();
        if (type == TypeEntiteRattachement.ENTREPRISE) {
            u.setEntreprise(entrepriseRepository.findById(entiteId).orElseThrow());
        } else {
            u.setAutoriteContractante(autoriteContractanteRepository.findById(entiteId).orElseThrow());
        }
        u = utilisateurRepository.save(u);

        UtilisateurDto result = toDto(u);
        auditService.log(AuditAction.CREATE, "Utilisateur", String.valueOf(u.getId()),
                Map.of("username", u.getUsername(), "role", u.getRole().name(),
                        "entite", type.name() + ":" + entiteId));
        return result;
    }

    @Transactional
    public void definirActif(TypeEntiteRattachement type, Long entiteId, Long utilisateurId, boolean actif) {
        Utilisateur u = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Utilisateur non trouvé: " + utilisateurId));
        // Le compte doit appartenir à l'entité de l'URL : sans ce contrôle, la route deviendrait une
        // activation universelle déguisée en action sur une fiche.
        Long rattachement = type == TypeEntiteRattachement.ENTREPRISE
                ? (u.getEntreprise() != null ? u.getEntreprise().getId() : null)
                : (u.getAutoriteContractante() != null ? u.getAutoriteContractante().getId() : null);
        if (rattachement == null || !rattachement.equals(entiteId)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Ce compte n'est pas rattaché à cette entité");
        }
        u.setActif(actif);
        utilisateurRepository.save(u);
        auditService.log(AuditAction.UPDATE, "Utilisateur", String.valueOf(u.getId()),
                Map.of("actif", actif, "entite", type.name() + ":" + entiteId));
    }

    private void assertEntiteExiste(TypeEntiteRattachement type, Long entiteId) {
        boolean existe = type == TypeEntiteRattachement.ENTREPRISE
                ? entrepriseRepository.existsById(entiteId)
                : autoriteContractanteRepository.existsById(entiteId);
        if (!existe) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entité non trouvée: " + entiteId);
        }
    }

    private void assertRoleCoherent(TypeEntiteRattachement type, Role role) {
        if (role == null || !DemandeRattachementService.ROLES_DEMANDABLES.contains(role)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Rôle non recevable pour un compte rattaché à une entité");
        }
        boolean roleEntreprise = role == Role.ENTREPRISE;
        if (roleEntreprise != (type == TypeEntiteRattachement.ENTREPRISE)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Le rôle ne correspond pas au type d'entité");
        }
    }

    private UtilisateurDto toDto(Utilisateur u) {
        return UtilisateurDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .role(u.getRole())
                .nomComplet(u.getNomComplet())
                .email(u.getEmail())
                .actif(u.getActif())
                .build();
    }
}
