package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeExplication;
import mr.gov.finances.sgci.domain.entity.DemandeExplicationMessage;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ContexteExplication;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutExplication;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DemandeExplicationRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.CreateDemandeExplicationRequest;
import mr.gov.finances.sgci.web.dto.DemandeExplicationDto;
import mr.gov.finances.sgci.web.dto.DemandeExplicationMessageDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandeExplicationService {

    private static final Set<Role> COMMISSION_ROLES = EnumSet.of(
            Role.DGD, Role.DGTCP, Role.DGI, Role.DGB, Role.PRESIDENT
    );

    private static final Set<Role> DESTINATAIRE_ROLES = EnumSet.of(
            Role.DGD, Role.DGTCP, Role.DGI, Role.DGB, Role.PRESIDENT
    );

    private static final Set<StatutDemande> CORRECTION_ALLOWED = EnumSet.of(
            StatutDemande.RECUE, StatutDemande.INCOMPLETE, StatutDemande.RECEVABLE,
            StatutDemande.EN_EVALUATION, StatutDemande.EN_VALIDATION
    );

    private static final Set<StatutCertificat> CERTIFICAT_ALLOWED = EnumSet.of(
            StatutCertificat.EN_CONTROLE, StatutCertificat.INCOMPLETE, StatutCertificat.A_RECONTROLER
    );

    private static final Set<StatutUtilisation> UTILISATION_FORBIDDEN = EnumSet.of(
            StatutUtilisation.LIQUIDEE, StatutUtilisation.APUREE, StatutUtilisation.REJETEE, StatutUtilisation.CLOTUREE
    );

    private final DemandeExplicationRepository explicationRepository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final CertificatCreditRepository certificatCreditRepository;
    private final UtilisationCreditRepository utilisationCreditRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DemandeCorrectionService demandeCorrectionService;
    private final CertificatCreditService certificatCreditService;
    private final UtilisationCreditService utilisationCreditService;
    private final NotificationService notificationService;
    private final NotificationNavigationHelper notificationNavigationHelper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DemandeExplicationDto> list(ContexteExplication contexte, Long dossierId, AuthenticatedUser user) {
        assertCommissionMember(user);
        assertDossierAccessible(contexte, dossierId, user);

        List<DemandeExplication> threads = switch (contexte) {
            case CORRECTION -> explicationRepository.findByDemandeCorrectionIdWithMessages(dossierId);
            case CERTIFICAT -> explicationRepository.findByCertificatCreditIdWithMessages(dossierId);
            case UTILISATION -> explicationRepository.findByUtilisationCreditIdWithMessages(dossierId);
        };
        return threads.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public DemandeExplicationDto create(CreateDemandeExplicationRequest request, AuthenticatedUser user) {
        assertCommissionMember(user);
        if (request == null || request.getContexte() == null || request.getDossierId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Requête invalide");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le message est obligatoire");
        }
        if (request.getRoleDestinataire() == null || !DESTINATAIRE_ROLES.contains(request.getRoleDestinataire())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le rôle destinataire doit être un membre de la commission (DGD, DGTCP, DGI, DGB, Président)");
        }

        assertDossierAccessible(request.getContexte(), request.getDossierId(), user);
        assertDossierAllowsExplication(request.getContexte(), request.getDossierId());

        Utilisateur auteur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        DemandeExplication.DemandeExplicationBuilder builder = DemandeExplication.builder()
                .contexte(request.getContexte())
                .roleDestinataire(request.getRoleDestinataire())
                .messageInitial(request.getMessage().trim())
                .statut(StatutExplication.OUVERTE)
                .auteur(auteur)
                .roleAuteur(user.getRole())
                .dateOuverture(Instant.now());

        linkDossier(builder, request.getContexte(), request.getDossierId());
        DemandeExplication saved = explicationRepository.save(builder.build());

        auditService.log(AuditAction.CREATE, "DemandeExplication", String.valueOf(saved.getId()),
                Map.of("contexte", request.getContexte().name(), "dossierId", request.getDossierId(),
                        "roleDestinataire", request.getRoleDestinataire().name()));

        notifyCommission(saved, user.getUserId(),
                "Nouvelle demande d'explication — destinataire visé : " + request.getRoleDestinataire().name());

        return toDto(saved);
    }

    @Transactional
    public DemandeExplicationMessageDto addMessage(Long explicationId, String message, AuthenticatedUser user) {
        assertCommissionMember(user);
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le message est obligatoire");
        }

        DemandeExplication thread = explicationRepository.findById(explicationId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande d'explication non trouvée"));

        assertDossierAccessible(thread.getContexte(), resolveDossierId(thread), user);

        if (thread.getStatut() == StatutExplication.FERMEE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Ce fil est fermé ; plus de réponse possible");
        }

        Utilisateur auteur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        DemandeExplicationMessage msg = DemandeExplicationMessage.builder()
                .demandeExplication(thread)
                .message(message.trim())
                .auteur(auteur)
                .roleAuteur(user.getRole())
                .createdAt(Instant.now())
                .build();
        thread.getMessages().add(msg);
        explicationRepository.save(thread);

        auditService.log(AuditAction.UPDATE, "DemandeExplication", String.valueOf(thread.getId()),
                Map.of("action", "reply", "messageId", msg.getId()));

        notifyCommission(thread, user.getUserId(),
                "Nouvelle réponse sur une demande d'explication (" + user.getRole().name() + ")");

        return toMessageDto(msg);
    }

    @Transactional
    public DemandeExplicationDto fermer(Long explicationId, AuthenticatedUser user) {
        assertCommissionMember(user);

        DemandeExplication thread = explicationRepository.findByIdWithMessages(explicationId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande d'explication non trouvée"));

        assertDossierAccessible(thread.getContexte(), resolveDossierId(thread), user);

        if (thread.getStatut() == StatutExplication.FERMEE) {
            return toDto(thread);
        }

        boolean auteur = thread.getAuteur() != null && thread.getAuteur().getId().equals(user.getUserId());
        boolean president = user.getRole() == Role.PRESIDENT;
        if (!auteur && !president) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seul l'auteur du fil ou le Président peut fermer la demande d'explication");
        }

        thread.setStatut(StatutExplication.FERMEE);
        thread.setDateFermeture(Instant.now());
        thread = explicationRepository.save(thread);

        auditService.log(AuditAction.UPDATE, "DemandeExplication", String.valueOf(thread.getId()),
                Map.of("action", "fermer", "statut", StatutExplication.FERMEE.name()));

        notifyCommission(thread, user.getUserId(), "Demande d'explication fermée");

        return toDto(thread);
    }

    private void assertCommissionMember(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (!COMMISSION_ROLES.contains(user.getRole())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED,
                    "Accès réservé aux membres de la commission (DGD, DGTCP, DGI, DGB, Président)");
        }
    }

    private void assertDossierAccessible(ContexteExplication contexte, Long dossierId, AuthenticatedUser user) {
        switch (contexte) {
            case CORRECTION -> demandeCorrectionService.findById(dossierId, user);
            case CERTIFICAT -> certificatCreditService.findById(dossierId, user);
            case UTILISATION -> utilisationCreditService.findById(dossierId, user);
        }
    }

    private void assertDossierAllowsExplication(ContexteExplication contexte, Long dossierId) {
        switch (contexte) {
            case CORRECTION -> {
                DemandeCorrection d = demandeCorrectionRepository.findById(dossierId)
                        .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande non trouvée"));
                if (!CORRECTION_ALLOWED.contains(d.getStatut())) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                            "Demande d'explication impossible en statut " + d.getStatut());
                }
            }
            case CERTIFICAT -> {
                CertificatCredit c = certificatCreditRepository.findById(dossierId)
                        .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat non trouvé"));
                if (!CERTIFICAT_ALLOWED.contains(c.getStatut())) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                            "Demande d'explication impossible en statut certificat " + c.getStatut());
                }
            }
            case UTILISATION -> {
                UtilisationCredit u = utilisationCreditRepository.findById(dossierId)
                        .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation non trouvée"));
                if (UTILISATION_FORBIDDEN.contains(u.getStatut())) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                            "Demande d'explication impossible en statut utilisation " + u.getStatut());
                }
            }
        }
    }

    private void linkDossier(DemandeExplication.DemandeExplicationBuilder builder, ContexteExplication contexte, Long dossierId) {
        switch (contexte) {
            case CORRECTION -> builder.demandeCorrection(demandeCorrectionRepository.findById(dossierId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande non trouvée")));
            case CERTIFICAT -> builder.certificatCredit(certificatCreditRepository.findById(dossierId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat non trouvé")));
            case UTILISATION -> builder.utilisationCredit(utilisationCreditRepository.findById(dossierId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation non trouvée")));
        }
    }

    private Long resolveDossierId(DemandeExplication thread) {
        if (thread.getDemandeCorrection() != null) {
            return thread.getDemandeCorrection().getId();
        }
        if (thread.getCertificatCredit() != null) {
            return thread.getCertificatCredit().getId();
        }
        if (thread.getUtilisationCredit() != null) {
            return thread.getUtilisationCredit().getId();
        }
        throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Fil sans dossier parent");
    }

    private void notifyCommission(DemandeExplication thread, Long excludeUserId, String message) {
        List<Long> userIds = new ArrayList<>();
        for (Role role : COMMISSION_ROLES) {
            utilisateurRepository.findByRoleAndActifTrue(role).stream()
                    .map(Utilisateur::getId)
                    .filter(id -> excludeUserId == null || !id.equals(excludeUserId))
                    .forEach(userIds::add);
        }
        if (userIds.isEmpty()) {
            return;
        }
        Long entityId = resolveDossierId(thread);
        Map<String, Object> payload = new HashMap<>();
        payload.put("explicationId", thread.getId());
        payload.put("contexte", thread.getContexte().name());
        payload.put("dossierId", entityId);
        payload.put("roleDestinataire", thread.getRoleDestinataire().name());
        payload.put("statut", thread.getStatut().name());
        payload.put("redirectPath", notificationNavigationHelper.buildRedirectPath(thread.getContexte(), entityId));

        notificationService.notifyUsers(
                userIds,
                NotificationType.DEMANDE_EXPLICATION,
                "DemandeExplication",
                thread.getId(),
                message,
                payload
        );
    }

    private DemandeExplicationDto toDto(DemandeExplication entity) {
        Long dossierId = resolveDossierId(entity);
        List<DemandeExplicationMessageDto> msgs = entity.getMessages() == null
                ? List.of()
                : entity.getMessages().stream().map(this::toMessageDto).collect(Collectors.toList());
        return DemandeExplicationDto.builder()
                .id(entity.getId())
                .contexte(entity.getContexte())
                .dossierId(dossierId)
                .roleDestinataire(entity.getRoleDestinataire())
                .messageInitial(entity.getMessageInitial())
                .statut(entity.getStatut())
                .auteurId(entity.getAuteur() != null ? entity.getAuteur().getId() : null)
                .auteurNom(entity.getAuteur() != null ? entity.getAuteur().getNomComplet() : null)
                .roleAuteur(entity.getRoleAuteur())
                .dateOuverture(entity.getDateOuverture())
                .dateFermeture(entity.getDateFermeture())
                .messages(msgs)
                .build();
    }

    private DemandeExplicationMessageDto toMessageDto(DemandeExplicationMessage msg) {
        return DemandeExplicationMessageDto.builder()
                .id(msg.getId())
                .message(msg.getMessage())
                .auteurId(msg.getAuteur() != null ? msg.getAuteur().getId() : null)
                .auteurNom(msg.getAuteur() != null ? msg.getAuteur().getNomComplet() : null)
                .roleAuteur(msg.getRoleAuteur())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
