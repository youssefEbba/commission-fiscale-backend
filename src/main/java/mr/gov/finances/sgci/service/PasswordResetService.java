package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DemandeResetPassword;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemandeResetPassword;
import mr.gov.finances.sgci.repository.DemandeResetPasswordRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.web.dto.CheckEmailResponse;
import mr.gov.finances.sgci.web.dto.DemandeResetPasswordDto;
import mr.gov.finances.sgci.web.dto.PasswordResetRequestResponse;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String PERMISSION_RESET = "user.reset";
    private static final String GENERIC_REQUEST_MESSAGE =
            "Si l'e-mail est enregistré, votre demande a été transmise à l'administrateur.";
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UtilisateurRepository utilisateurRepository;
    private final DemandeResetPasswordRepository demandeResetPasswordRepository;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public CheckEmailResponse checkEmail(String email) {
        String normalized = normalizeEmail(email);
        List<Utilisateur> users = findActiveUsersByEmail(normalized);
        return CheckEmailResponse.builder().exists(!users.isEmpty()).build();
    }

    @Transactional
    public PasswordResetRequestResponse submitRequest(String email) {
        String normalized = normalizeEmail(email);
        List<Utilisateur> users = findActiveUsersByEmail(normalized);
        if (users.isEmpty()) {
            return PasswordResetRequestResponse.builder().message(GENERIC_REQUEST_MESSAGE).build();
        }
        if (users.size() > 1) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Plusieurs comptes sont associés à cet e-mail. Veuillez contacter l'administrateur.");
        }
        Utilisateur user = users.get(0);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return PasswordResetRequestResponse.builder().message(GENERIC_REQUEST_MESSAGE).build();
        }
        if (demandeResetPasswordRepository.existsByUtilisateurIdAndStatut(
                user.getId(), StatutDemandeResetPassword.EN_ATTENTE)) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Une demande de réinitialisation est déjà en attente pour ce compte.");
        }

        DemandeResetPassword demande = DemandeResetPassword.builder()
                .utilisateur(user)
                .email(user.getEmail().trim())
                .statut(StatutDemandeResetPassword.EN_ATTENTE)
                .build();
        demande = demandeResetPasswordRepository.save(demande);

        notifyAdminsNewRequest(demande, user);
        auditService.log(AuditAction.CREATE, "DemandeResetPassword", String.valueOf(demande.getId()),
                Map.of("utilisateurId", user.getId(), "email", demande.getEmail()));

        return PasswordResetRequestResponse.builder().message(GENERIC_REQUEST_MESSAGE).build();
    }

    @Transactional(readOnly = true)
    public List<DemandeResetPasswordDto> listRequests(StatutDemandeResetPassword statut) {
        List<DemandeResetPassword> demandes = statut != null
                ? demandeResetPasswordRepository.findByStatutOrderByDateCreationDesc(statut)
                : demandeResetPasswordRepository.findAllByOrderByDateCreationDesc();
        return demandes.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DemandeResetPasswordDto getRequest(Long id) {
        return toDto(findDemandeOrThrow(id));
    }

    @Transactional
    public DemandeResetPasswordDto approve(Long id, Long adminUserId) {
        DemandeResetPassword demande = findDemandeOrThrow(id);
        if (demande.getStatut() != StatutDemandeResetPassword.EN_ATTENTE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Seule une demande en attente peut être approuvée.");
        }
        Utilisateur user = demande.getUtilisateur();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "L'utilisateur n'a pas d'adresse e-mail enregistrée.");
        }
        Utilisateur admin = utilisateurRepository.findById(adminUserId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Administrateur introuvable"));

        String newPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        utilisateurRepository.save(user);

        demande.setStatut(StatutDemandeResetPassword.APPROUVEE);
        demande.setDateTraitement(Instant.now());
        demande.setTraitePar(admin);
        demande = demandeResetPasswordRepository.save(demande);

        emailService.sendPasswordResetApproved(user.getEmail(), user.getUsername(), newPassword);
        notificationService.notifyUser(user.getId(), NotificationType.PASSWORD_RESET_TRAITEE,
                "DemandeResetPassword", demande.getId(),
                "Votre demande de réinitialisation de mot de passe a été approuvée. Consultez votre e-mail.",
                Map.of("statut", StatutDemandeResetPassword.APPROUVEE.name()));

        auditService.log(AuditAction.UPDATE, "DemandeResetPassword", String.valueOf(demande.getId()),
                Map.of("statut", StatutDemandeResetPassword.APPROUVEE.name(), "utilisateurId", user.getId()));

        return toDto(demande);
    }

    @Transactional
    public DemandeResetPasswordDto reject(Long id, Long adminUserId, String motif) {
        DemandeResetPassword demande = findDemandeOrThrow(id);
        if (demande.getStatut() != StatutDemandeResetPassword.EN_ATTENTE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Seule une demande en attente peut être refusée.");
        }
        Utilisateur admin = utilisateurRepository.findById(adminUserId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Administrateur introuvable"));
        Utilisateur user = demande.getUtilisateur();

        demande.setStatut(StatutDemandeResetPassword.REFUSEE);
        demande.setDateTraitement(Instant.now());
        demande.setTraitePar(admin);
        demande.setMotifRefus(motif != null && !motif.isBlank() ? motif.trim() : null);
        demande = demandeResetPasswordRepository.save(demande);

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            emailService.sendPasswordResetRejected(user.getEmail(), user.getUsername(), demande.getMotifRefus());
        }
        notificationService.notifyUser(user.getId(), NotificationType.PASSWORD_RESET_TRAITEE,
                "DemandeResetPassword", demande.getId(),
                "Votre demande de réinitialisation de mot de passe a été refusée.",
                Map.of("statut", StatutDemandeResetPassword.REFUSEE.name(),
                        "motif", demande.getMotifRefus() != null ? demande.getMotifRefus() : ""));

        auditService.log(AuditAction.UPDATE, "DemandeResetPassword", String.valueOf(demande.getId()),
                Map.of("statut", StatutDemandeResetPassword.REFUSEE.name(), "utilisateurId", user.getId()));

        return toDto(demande);
    }

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Africa/Nouakchott"));

    private void notifyAdminsNewRequest(DemandeResetPassword demande, Utilisateur user) {
        List<Long> adminIds = findAdminUserIdsWithResetPermission();
        if (adminIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("demandeId", demande.getId());
        payload.put("username", user.getUsername());
        payload.put("email", demande.getEmail());
        notificationService.notifyUsers(adminIds, NotificationType.PASSWORD_RESET_REQUEST,
                "DemandeResetPassword", demande.getId(),
                "Demande de réinitialisation de mot de passe pour " + user.getUsername(),
                payload);

        // Envoyer un e-mail à chaque admin ayant une adresse e-mail
        String dateFormatted = DATE_FMT.format(demande.getDateCreation() != null ? demande.getDateCreation() : Instant.now());
        utilisateurRepository.findByRoleInAndActifTrue(
                Arrays.stream(mr.gov.finances.sgci.domain.enums.Role.values())
                        .filter(role -> permissionService.findPermissionCodesByRole(role).contains(PERMISSION_RESET))
                        .toList()
        ).stream()
                .filter(admin -> admin.getEmail() != null && !admin.getEmail().isBlank())
                .distinct()
                .forEach(admin -> emailService.sendPasswordResetRequestToAdmin(
                        admin.getEmail(),
                        user.getUsername(),
                        demande.getEmail(),
                        dateFormatted
                ));
    }

    private List<Long> findAdminUserIdsWithResetPermission() {
        List<Role> roles = Arrays.stream(Role.values())
                .filter(role -> permissionService.findPermissionCodesByRole(role).contains(PERMISSION_RESET))
                .toList();
        if (roles.isEmpty()) {
            return List.of();
        }
        return utilisateurRepository.findByRoleInAndActifTrue(roles).stream()
                .map(Utilisateur::getId)
                .distinct()
                .toList();
    }

    private List<Utilisateur> findActiveUsersByEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return utilisateurRepository.findByEmailIgnoreCaseAndActifTrue(email.trim());
    }

    private DemandeResetPassword findDemandeOrThrow(Long id) {
        return demandeResetPasswordRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Demande de réinitialisation non trouvée: " + id));
    }

    private DemandeResetPasswordDto toDto(DemandeResetPassword d) {
        Utilisateur u = d.getUtilisateur();
        Utilisateur traitePar = d.getTraitePar();
        return DemandeResetPasswordDto.builder()
                .id(d.getId())
                .utilisateurId(u != null ? u.getId() : null)
                .username(u != null ? u.getUsername() : null)
                .nomComplet(u != null ? u.getNomComplet() : null)
                .email(d.getEmail())
                .statut(d.getStatut())
                .dateCreation(d.getDateCreation())
                .dateTraitement(d.getDateTraitement())
                .traiteParId(traitePar != null ? traitePar.getId() : null)
                .traiteParUsername(traitePar != null ? traitePar.getUsername() : null)
                .motifRefus(d.getMotifRefus())
                .build();
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim();
    }

    String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(secureRandom.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
