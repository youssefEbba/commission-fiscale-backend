package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.RejetTempResponse;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.DecisionCorrectionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.AdminVisaCorrectionResultDto;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionDto;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import mr.gov.finances.sgci.web.dto.DocumentDto;
import mr.gov.finances.sgci.web.dto.VisaCorrectionStatutDto;
import mr.gov.finances.sgci.workflow.DemandeCorrectionWorkflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionCorrectionService {

    private static final Set<StatutDemande> DECISION_ALLOWED_STATUTS = EnumSet.of(
            StatutDemande.RECUE, StatutDemande.INCOMPLETE, StatutDemande.RECEVABLE,
            StatutDemande.EN_EVALUATION, StatutDemande.EN_VALIDATION
    );

    /** Rôles de la commission qui posent un visa, dans l'ordre d'affichage, Président (adoption) en dernier. */
    private static final List<Role> VISA_ROLES_ORDER = List.of(
            Role.DGD, Role.DGTCP, Role.DGI, Role.DGB, Role.PRESIDENT
    );

    private final DecisionCorrectionRepository decisionRepository;
    private final DemandeCorrectionRepository demandeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final WorkflowNotificationHelper workflowNotificationHelper;
    private final DemandeCorrectionWorkflow demandeCorrectionWorkflow;
    private final DemandeCorrectionService demandeCorrectionService;
    private final DocumentService documentService;
    private final VisaRequirementResolver visaRequirementResolver;
    private final AuditService auditService;

    @Transactional
    public DecisionCorrectionDto resolveRejetTemp(Long decisionId, AuthenticatedUser user) {
        if (decisionId == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Décision invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }

        DecisionCorrection decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision correction non trouvée: " + decisionId));

        if (decision.getDecision() != DecisionCorrectionType.REJET_TEMP || decision.getRejetTempStatus() != RejetTempStatus.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Résolution interdite: la décision n'est pas un REJET_TEMP OUVERT");
        }
        if (decision.getRole() == null || decision.getRole() != user.getRole()) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Résolution interdite: rôle non autorisé");
        }

        decision.setRejetTempStatus(RejetTempStatus.RESOLU);
        decision.setRejetTempResolvedAt(Instant.now());
        decision = decisionRepository.save(decision);

        Long demandeId = decision.getDemandeCorrection() != null ? decision.getDemandeCorrection().getId() : null;
        if (demandeId != null) {
            boolean anyOpen = !decisionRepository.findByDemandeCorrectionIdAndDecisionAndRejetTempStatus(
                    demandeId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT
            ).isEmpty();

            if (!anyOpen) {
                DemandeCorrection demande = demandeRepository.findById(demandeId).orElse(null);
                if (demande != null && demande.getStatut() == StatutDemande.INCOMPLETE) {
                    demande.setStatut(StatutDemande.RECEVABLE);
                    demandeRepository.save(demande);
                    workflowNotificationHelper.correctionRejetTempResolu(demande, user);
                }
            }
        }

        return toDto(decision);
    }

    @Transactional
    public DecisionCorrectionDto saveDecision(Long demandeId, DecisionCorrectionType decision, String motifRejet, Set<String> documentsDemandes, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Role role = user.getRole();
        if (role != Role.DGD && role != Role.DGTCP && role != Role.DGI && role != Role.DGB && role != Role.PRESIDENT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Rôle non autorisé pour la décision: " + role);
        }

        DemandeCorrection demande = demandeRepository.findById(demandeId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + demandeId));
        Set<Role> requiredRoles = visaRequirementResolver.requiredRolesForCorrection(demande);

        if (role != Role.PRESIDENT && !requiredRoles.contains(role)) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Rôle non concerné par cette demande (crédit associé nul): " + role
                            + ". Visas requis: " + requiredRoles);
        }

        Role firstVisaRole = visaRequirementResolver.firstVisaRoleForCorrection(demande);
        if (firstVisaRole != null && role != firstVisaRole && role != Role.PRESIDENT) {
            boolean firstVisaPose = decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecision(
                    demandeId, firstVisaRole, DecisionCorrectionType.VISA);
            if (!firstVisaPose) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Le visa " + firstVisaRole + " est requis en premier");
            }
        }
        if (decision == DecisionCorrectionType.REJET_TEMP) {
            if (motifRejet == null || motifRejet.isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de rejet est obligatoire");
            }
            if (documentsDemandes == null || documentsDemandes.isEmpty()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La liste des documents demandés est obligatoire");
            }
        }

        StatutDemande statutAvantDecision = demande.getStatut();

        if (!DECISION_ALLOWED_STATUTS.contains(demande.getStatut())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Décision impossible: la demande est en statut " + demande.getStatut()
                            + ". Les décisions ne sont autorisées qu'en statut: " + DECISION_ALLOWED_STATUTS);
        }

        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        boolean visaAlreadyForRole = decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecision(
                demandeId, role, DecisionCorrectionType.VISA);
        if (visaAlreadyForRole) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Décision impossible: un visa a déjà été accordé par ce rôle. Le visa clôture les interactions sur cette demande.");
        }

        if (decision == DecisionCorrectionType.VISA) {
            boolean openRejetForRole = decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecisionAndRejetTempStatus(
                    demandeId, role, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT);
            if (openRejetForRole) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "VISA impossible: un ou plusieurs rejets temporaires sont encore ouverts pour ce rôle. "
                                + "Résolvez-les via PUT .../decisions/{id}/resolve pour chaque rejet concerné.");
            }
            if (role == Role.DGI && visaRequirementResolver.isCreditInterieurDocumentRequiredForCorrection(demande)) {
                documentService.assertActiveDocumentPresent(demandeId, TypeDocument.CREDIT_INTERIEUR.name());
            }
            if (role == Role.DGD && visaRequirementResolver.isOffreFiscaleCorrigeeRequiredForCorrection(demande)) {
                documentService.assertActiveDocumentPresent(demandeId, TypeDocument.OFFRE_FISCALE_CORRIGEE.name());
            }
        }

        DecisionCorrection entity = DecisionCorrection.builder()
                .demandeCorrection(demande)
                .role(role)
                .build();

        entity.setDecision(decision);
        entity.setMotifRejet(decision == DecisionCorrectionType.REJET_TEMP ? motifRejet : null);
        entity.setDocumentsDemandes(decision == DecisionCorrectionType.REJET_TEMP
                ? new HashSet<>(documentsDemandes)
                : new HashSet<>());
        entity.setDateDecision(Instant.now());
        entity.setRejetTempStatus(decision == DecisionCorrectionType.REJET_TEMP
                ? RejetTempStatus.OUVERT
                : RejetTempStatus.RESOLU);
        entity.setRejetTempResolvedAt(decision == DecisionCorrectionType.REJET_TEMP ? null : Instant.now());
        entity.setUtilisateur(utilisateur);

        if (decision == DecisionCorrectionType.REJET_TEMP) {
            demande.setStatut(StatutDemande.INCOMPLETE);
        }

        entity = decisionRepository.save(entity);

        if (decision == DecisionCorrectionType.VISA) {
            applyStatutAutomatiqueApresVisa(demande, role, demandeId);
        }

        demandeRepository.save(demande);

        if (decision == DecisionCorrectionType.VISA && demande.getStatut() != statutAvantDecision) {
            demandeCorrectionService.notifyCorrectionStatutChange(demande, demande.getStatut(), user, null, false);
        }

        notifyDecision(demande, entity, user);
        return toDto(entity);
    }

    /**
     * Transitions métier sans passer par {@code PATCH .../statut} (évite 403 pour les rôles DGD / etc.) :
     * <ul>
     *   <li>{@code RECUE} ou {@code RECEVABLE} + VISA (DGD/DGTCP/DGI/DGB) → {@code EN_EVALUATION}</li>
     *   <li>Lorsque les quatre visas sont posés (DGD, DGTCP, DGI, DGB) et statut {@code EN_EVALUATION} → {@code EN_VALIDATION}</li>
     * </ul>
     */
    private void applyStatutAutomatiqueApresVisa(DemandeCorrection demande, Role roleDecideur, Long demandeId) {
        if (roleDecideur != Role.DGD && roleDecideur != Role.DGTCP && roleDecideur != Role.DGI && roleDecideur != Role.DGB) {
            return;
        }
        StatutDemande s = demande.getStatut();
        if (s == StatutDemande.RECUE) {
            demandeCorrectionWorkflow.validateTransition(StatutDemande.RECUE, StatutDemande.EN_EVALUATION);
            demande.setStatut(StatutDemande.EN_EVALUATION);
            s = demande.getStatut();
        } else if (s == StatutDemande.RECEVABLE) {
            demandeCorrectionWorkflow.validateTransition(StatutDemande.RECEVABLE, StatutDemande.EN_EVALUATION);
            demande.setStatut(StatutDemande.EN_EVALUATION);
            s = demande.getStatut();
        }

        Set<Role> requiredRoles = visaRequirementResolver.requiredRolesForCorrection(demande);
        boolean tousVisas = requiredRoles.stream()
                .allMatch(r -> decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecision(
                        demandeId, r, DecisionCorrectionType.VISA));
        if (tousVisas && s == StatutDemande.EN_EVALUATION) {
            demandeCorrectionWorkflow.validateTransition(StatutDemande.EN_EVALUATION, StatutDemande.EN_VALIDATION);
            demande.setStatut(StatutDemande.EN_VALIDATION);
        }
    }

    /**
     * Etat des visas de la commission sur une demande : qui doit viser, qui a deja vise, quel
     * document est exige avant chaque visa et si l'administrateur peut viser a la place du titulaire.
     * Le President figure en dernier : son visa est l'adoption ({@code EN_VALIDATION -> ADOPTEE}).
     */
    @Transactional(readOnly = true)
    public List<VisaCorrectionStatutDto> visaStatuts(Long demandeId) {
        DemandeCorrection demande = demandeRepository.findById(demandeId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + demandeId));
        return buildVisaStatuts(demande);
    }

    /**
     * Visa posé par l'administrateur système (ADMIN_SI) à la place d'un membre de la commission ou
     * du Président, lorsque le titulaire n'est pas en mesure de se prononcer.
     * <p>
     * Les règles métier du visa restent intégralement appliquées : rôle concerné par la demande,
     * statut autorisé, visa pas déjà posé, aucun rejet temporaire ouvert, visa préalable respecté
     * et, surtout, <b>document exigé présent</b> :
     * <ul>
     *   <li>DGD -> {@code OFFRE_FISCALE_CORRIGEE} lorsque le crédit extérieur est positif ;</li>
     *   <li>DGI -> {@code CREDIT_INTERIEUR} lorsque la demande est exclusivement intérieure ;</li>
     *   <li>PRESIDENT -> {@code LETTRE_ADOPTION}.</li>
     * </ul>
     * L'administrateur peut téléverser ce document dans le même appel ({@code file}) ; s'il ne
     * l'envoie pas et qu'aucune version active n'existe, le visa est refusé. Motif obligatoire,
     * journalisé sous {@link AuditAction#ADMIN_CORRECTION} et tracé sur le visa
     * ({@code visaParAdmin}, {@code motifAdmin}).
     */
    @Transactional
    public AdminVisaCorrectionResultDto adminVisaPourRole(Long demandeId,
                                                          Role roleCible,
                                                          String motif,
                                                          MultipartFile file,
                                                          AuthenticatedUser user) throws IOException {
        if (user == null || user.getRole() != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Visa administrateur réservé à l'administrateur système");
        }
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le motif du visa administrateur est obligatoire");
        }
        if (roleCible == null || !VISA_ROLES_ORDER.contains(roleCible)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Rôle de visa invalide. Rôles attendus: " + VISA_ROLES_ORDER);
        }

        DemandeCorrection demande = demandeRepository.findById(demandeId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + demandeId));

        // Contrôles métier d'abord : un visa refusé ne doit pas laisser de fichier orphelin sur MinIO.
        if (roleCible == Role.PRESIDENT) {
            assertAdoptionPossible(demande);
        } else {
            assertVisaMembrePossible(demande, roleCible);
        }

        String codeDocumentRequis = codeDocumentRequisPourVisa(demande, roleCible);
        boolean fichierFourni = file != null && !file.isEmpty();
        if (fichierFourni && codeDocumentRequis == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Aucun document n'est attendu pour le visa " + roleCible + " sur cette demande");
        }

        DocumentDto document = null;
        if (fichierFourni) {
            document = documentService.adminReplace(demandeId, codeDocumentRequis, motif, file, user);
        }
        if (codeDocumentRequis != null) {
            documentService.assertActiveDocumentPresent(demandeId, codeDocumentRequis,
                    "avant le visa " + roleCible + " (à téléverser avec le visa administrateur)");
        }

        DemandeCorrectionDto demandeDto;
        DecisionCorrectionDto decisionDto = null;

        if (roleCible == Role.PRESIDENT) {
            demandeDto = demandeCorrectionService.adminAdopterPourPresident(demandeId, motif, user);
        } else {
            decisionDto = enregistrerVisaAdminMembre(demande, roleCible, motif, user);
            demandeDto = demandeCorrectionService.findById(demandeId, user);
        }

        DemandeCorrection rafraichie = demandeRepository.findById(demandeId).orElse(demande);
        return AdminVisaCorrectionResultDto.builder()
                .demande(demandeDto)
                .decision(decisionDto)
                .document(document)
                .visas(buildVisaStatuts(rafraichie))
                .build();
    }

    /** Création du visa d'un membre (DGD / DGTCP / DGI / DGB) posé par l'administrateur. */
    private DecisionCorrectionDto enregistrerVisaAdminMembre(DemandeCorrection demande,
                                                             Role roleCible,
                                                             String motif,
                                                             AuthenticatedUser user) {
        Long demandeId = demande.getId();
        assertVisaMembrePossible(demande, roleCible);

        Utilisateur administrateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        StatutDemande statutAvantVisa = demande.getStatut();

        DecisionCorrection entity = DecisionCorrection.builder()
                .demandeCorrection(demande)
                .role(roleCible)
                .build();
        entity.setDecision(DecisionCorrectionType.VISA);
        entity.setMotifRejet(null);
        entity.setDocumentsDemandes(new HashSet<>());
        entity.setDateDecision(Instant.now());
        entity.setRejetTempStatus(RejetTempStatus.RESOLU);
        entity.setRejetTempResolvedAt(Instant.now());
        entity.setUtilisateur(administrateur);
        entity.setVisaParAdmin(Boolean.TRUE);
        entity.setMotifAdmin(motif);

        entity = decisionRepository.save(entity);

        applyStatutAutomatiqueApresVisa(demande, roleCible, demandeId);
        demandeRepository.save(demande);

        DecisionCorrectionDto dto = toDto(entity);
        auditService.log(AuditAction.ADMIN_CORRECTION, "DecisionCorrection", String.valueOf(entity.getId()), dto, motif);

        if (demande.getStatut() != statutAvantVisa) {
            demandeCorrectionService.notifyCorrectionStatutChange(demande, demande.getStatut(), user, null, false);
        }
        notifyDecision(demande, entity, user);
        return dto;
    }

    /**
     * Statut compatible avec l'adoption prononcée par l'administrateur à la place du Président
     * (le contrôle de la lettre d'adoption est fait séparément, après un éventuel téléversement).
     */
    private void assertAdoptionPossible(DemandeCorrection demande) {
        if (demande.getStatut() == StatutDemande.ADOPTEE || demande.getStatut() == StatutDemande.NOTIFIEE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Adoption impossible: la demande est déjà adoptée (statut " + demande.getStatut() + ")");
        }
        if (demande.getStatut() != StatutDemande.EN_VALIDATION) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Adoption impossible: la demande doit être en statut EN_VALIDATION (statut actuel: "
                            + demande.getStatut() + "). Les visas DGD / DGTCP / DGI / DGB doivent être posés au préalable.");
        }
    }

    /** Mêmes garde-fous que le visa posé par le titulaire lui-même (voir {@link #saveDecision}). */
    private void assertVisaMembrePossible(DemandeCorrection demande, Role role) {
        Long demandeId = demande.getId();

        Set<Role> requiredRoles = visaRequirementResolver.requiredRolesForCorrection(demande);
        if (!requiredRoles.contains(role)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Rôle non concerné par cette demande: " + role + ". Visas requis: " + requiredRoles);
        }
        if (!DECISION_ALLOWED_STATUTS.contains(demande.getStatut())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Visa impossible: la demande est en statut " + demande.getStatut()
                            + ". Visa autorisé uniquement en statut: " + DECISION_ALLOWED_STATUTS);
        }
        if (decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecision(demandeId, role, DecisionCorrectionType.VISA)) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Visa impossible: le rôle " + role + " a déjà visé cette demande.");
        }
        if (decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecisionAndRejetTempStatus(
                demandeId, role, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Visa impossible: un rejet temporaire du rôle " + role + " est encore ouvert. "
                            + "Il doit être résolu avant que le visa puisse être posé.");
        }
        Role firstVisaRole = visaRequirementResolver.firstVisaRoleForCorrection(demande);
        if (firstVisaRole != null && role != firstVisaRole
                && !decisionRepository.existsByDemandeCorrectionIdAndRoleAndDecision(
                        demandeId, firstVisaRole, DecisionCorrectionType.VISA)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le visa " + firstVisaRole + " est requis en premier");
        }
    }

    /** Code du document exigé avant le visa de ce rôle, {@code null} si aucun. */
    private String codeDocumentRequisPourVisa(DemandeCorrection demande, Role role) {
        if (role == Role.PRESIDENT) {
            return TypeDocument.LETTRE_ADOPTION.name();
        }
        if (role == Role.DGD && visaRequirementResolver.isOffreFiscaleCorrigeeRequiredForCorrection(demande)) {
            return TypeDocument.OFFRE_FISCALE_CORRIGEE.name();
        }
        if (role == Role.DGI && visaRequirementResolver.isCreditInterieurDocumentRequiredForCorrection(demande)) {
            return TypeDocument.CREDIT_INTERIEUR.name();
        }
        return null;
    }

    private List<VisaCorrectionStatutDto> buildVisaStatuts(DemandeCorrection demande) {
        Long demandeId = demande.getId();
        List<DecisionCorrection> decisions = decisionRepository.findByDemandeCorrectionId(demandeId);
        Set<Role> requiredRoles = visaRequirementResolver.requiredRolesForCorrection(demande);
        Role firstVisaRole = visaRequirementResolver.firstVisaRoleForCorrection(demande);
        Set<String> documentsActifs = new HashSet<>(documentService.findActiveDocumentTypes(demandeId));
        boolean firstVisaPose = firstVisaRole == null || decisions.stream()
                .anyMatch(d -> d.getRole() == firstVisaRole && d.getDecision() == DecisionCorrectionType.VISA);

        List<VisaCorrectionStatutDto> result = new ArrayList<>();
        for (Role role : VISA_ROLES_ORDER) {
            String codeDocumentRequis = codeDocumentRequisPourVisa(demande, role);
            boolean documentPresent = codeDocumentRequis == null || documentsActifs.contains(codeDocumentRequis);

            VisaCorrectionStatutDto.VisaCorrectionStatutDtoBuilder b = VisaCorrectionStatutDto.builder()
                    .role(role)
                    .codeDocumentRequis(codeDocumentRequis)
                    .documentRequisPresent(documentPresent);

            if (role == Role.PRESIDENT) {
                boolean adoptee = demande.getStatut() == StatutDemande.ADOPTEE
                        || demande.getStatut() == StatutDemande.NOTIFIEE;
                b.requis(true).pose(adoptee);
                if (adoptee) {
                    b.visableParAdmin(false).motifBlocage("La demande est déjà adoptée");
                } else if (demande.getStatut() != StatutDemande.EN_VALIDATION) {
                    b.visableParAdmin(false).motifBlocage(
                            "Adoption possible uniquement en statut EN_VALIDATION (statut actuel: "
                                    + demande.getStatut() + ")");
                } else {
                    b.visableParAdmin(true);
                }
                result.add(b.build());
                continue;
            }

            DecisionCorrection visa = decisions.stream()
                    .filter(d -> d.getRole() == role && d.getDecision() == DecisionCorrectionType.VISA)
                    .findFirst()
                    .orElse(null);
            boolean rejetTempOuvert = decisions.stream()
                    .anyMatch(d -> d.getRole() == role
                            && d.getDecision() == DecisionCorrectionType.REJET_TEMP
                            && d.getRejetTempStatus() == RejetTempStatus.OUVERT);
            boolean requis = requiredRoles.contains(role);
            boolean visaPrealableManquant = firstVisaRole != null && role != firstVisaRole && !firstVisaPose;

            b.requis(requis)
                    .pose(visa != null)
                    .rejetTempOuvert(rejetTempOuvert)
                    .visaPrealableManquant(visaPrealableManquant ? firstVisaRole : null);

            if (visa != null) {
                b.decisionId(visa.getId())
                        .datePose(visa.getDateDecision())
                        .utilisateurId(visa.getUtilisateur() != null ? visa.getUtilisateur().getId() : null)
                        .utilisateurNom(visa.getUtilisateur() != null ? visa.getUtilisateur().getNomComplet() : null)
                        .visaParAdmin(Boolean.TRUE.equals(visa.getVisaParAdmin()));
            }

            String blocage = null;
            if (!requis) {
                blocage = "Rôle non concerné par cette demande";
            } else if (visa != null) {
                blocage = "Visa déjà posé";
            } else if (!DECISION_ALLOWED_STATUTS.contains(demande.getStatut())) {
                blocage = "Visa impossible en statut " + demande.getStatut();
            } else if (rejetTempOuvert) {
                blocage = "Un rejet temporaire de ce rôle est encore ouvert";
            } else if (visaPrealableManquant) {
                blocage = "Le visa " + firstVisaRole + " est requis en premier";
            }
            b.visableParAdmin(blocage == null).motifBlocage(blocage);
            result.add(b.build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<DecisionCorrectionDto> findByDemande(Long demandeId) {
        return decisionRepository.findByDemandeCorrectionId(demandeId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Réouverture après réclamation acceptée : efface visas et rejets temporaires pour que chaque
     * membre de la commission doive à nouveau poser son visa.
     */
    @Transactional
    public void resetDecisionsForReclamationAcceptee(Long demandeId) {
        decisionRepository.deleteByDemandeCorrectionId(demandeId);
    }

    private DecisionCorrectionDto toDto(DecisionCorrection entity) {
        return DecisionCorrectionDto.builder()
                .id(entity.getId())
                .role(entity.getRole())
                .decision(entity.getDecision())
                .motifRejet(entity.getMotifRejet())
                .documentsDemandes(entity.getDocumentsDemandes())
                .dateDecision(entity.getDateDecision())
                .rejetTempStatus(entity.getRejetTempStatus())
                .rejetTempResolvedAt(entity.getRejetTempResolvedAt())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .visaParAdmin(Boolean.TRUE.equals(entity.getVisaParAdmin()))
                .motifAdmin(entity.getMotifAdmin())
                .rejetTempResponses(entity.getRejetTempResponses() != null
                        ? entity.getRejetTempResponses().stream().map(this::toResponseDto).collect(Collectors.toList())
                        : java.util.List.of())
                .build();
    }

    private mr.gov.finances.sgci.web.dto.RejetTempResponseDto toResponseDto(RejetTempResponse entity) {
        return mr.gov.finances.sgci.web.dto.RejetTempResponseDto.builder()
                .id(entity.getId())
                .message(entity.getMessage())
                .documentUrl(entity.getDocumentUrl())
                .codeDocument(entity.getCodeDocument())
                .documentVersion(entity.getDocumentVersion())
                .createdAt(entity.getCreatedAt())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .build();
    }

    private void notifyDecision(DemandeCorrection demande, DecisionCorrection decision, AuthenticatedUser user) {
        if (demande == null || decision == null || decision.getDecision() == null) {
            return;
        }
        workflowNotificationHelper.correctionDecision(demande, decision, user);
    }
}
