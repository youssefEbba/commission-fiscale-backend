package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DecisionTransfertCredit;
import mr.gov.finances.sgci.domain.entity.RejetTempResponse;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.WorkflowEventCode;
import mr.gov.finances.sgci.repository.DecisionTransfertCreditRepository;
import mr.gov.finances.sgci.repository.RejetTempResponseRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DecisionCreditDto;
import mr.gov.finances.sgci.web.dto.RejetTempResponseDto;
import mr.gov.finances.sgci.web.dto.VisaTransfertStatutDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionTransfertCreditService {

    private final DecisionTransfertCreditRepository decisionRepository;
    private final TransfertCreditRepository transfertRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentTransfertCreditService documentTransfertCreditService;
    private final RejetTempResponseService rejetTempResponseService;
    private final RejetTempResponseRepository rejetTempResponseRepository;
    private final WorkflowNotificationHelper workflowNotificationHelper;

    /**
     * Ordre imposé du circuit P7 : chaque direction atteste ce qui relève d'elle, et le Président
     * tranche en dernier sur un dossier déjà instruit.
     *
     * <p>La DGD libère le solde douanier, la DGI confirme le besoin intérieur, la DGTCP vérifie la
     * régularité financière. Le visa du Président, posé via {@code POST .../valider}, déclenche
     * l'écriture.
     */
    public static final List<Role> ORDRE_VISAS = List.of(Role.DGD, Role.DGI, Role.DGTCP, Role.PRESIDENT);

    /**
     * Cause du blocage du visa de ce rôle, {@code null} si rien ne s'y oppose.
     *
     * <p>Source de vérité unique : les assertions lèvent l'exception correspondante et l'écran
     * d'état expose le même code. Aucun client n'a donc à réécrire la règle de séquencement.
     */
    public String codeBlocageVisa(TransfertCredit transfert, Role role) {
        if (role == null || !ORDRE_VISAS.contains(role)) {
            return "ROLE_NON_HABILITE";
        }
        StatutTransfert st = transfert.getStatut();
        if (st == StatutTransfert.TRANSFERE || st == StatutTransfert.REJETE || st == StatutTransfert.ANNULEE) {
            return "STATUT_INCOMPATIBLE";
        }
        List<DecisionTransfertCredit> decisions = decisionRepository.findByTransfertCredit_Id(transfert.getId());
        if (aVise(decisions, role)) {
            return "VISA_DEJA_POSE";
        }
        boolean rejetOuvert = decisions.stream()
                .anyMatch(d -> d.getDecision() == DecisionCorrectionType.REJET_TEMP
                        && d.getRejetTempStatus() == RejetTempStatus.OUVERT);
        if (rejetOuvert) {
            return "REJET_TEMP_OUVERT";
        }
        if (visaPrealableManquant(decisions, role) != null) {
            return "VISA_PREALABLE_MANQUANT";
        }
        return null;
    }

    /** Premier rôle du circuit, avant celui-ci, qui n'a pas encore visé. */
    public Role visaPrealableManquant(List<DecisionTransfertCredit> decisions, Role role) {
        for (Role precedent : ORDRE_VISAS) {
            if (precedent == role) {
                return null;
            }
            if (!aVise(decisions, precedent)) {
                return precedent;
            }
        }
        return null;
    }

    private static boolean aVise(List<DecisionTransfertCredit> decisions, Role role) {
        return decisions.stream()
                .anyMatch(d -> d.getRole() == role && d.getDecision() == DecisionCorrectionType.VISA);
    }

    /** Exception correspondant à un code de blocage : message et code restent solidaires. */
    public ApiException exceptionBlocageVisa(String code, TransfertCredit transfert, Role role) {
        switch (code) {
            case "ROLE_NON_HABILITE":
                return ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                        "Rôle non habilité à viser un transfert : " + role
                                + ". Circuit attendu : " + ORDRE_VISAS);
            case "STATUT_INCOMPATIBLE":
                return new ApiException(HttpStatus.CONFLICT.value(), ApiErrorCode.STATUT_INCOMPATIBLE,
                        "Décision impossible pour le statut : " + transfert.getStatut());
            case "VISA_DEJA_POSE":
                return new ApiException(HttpStatus.CONFLICT.value(), ApiErrorCode.CONFLICT,
                        "Un visa a déjà été posé par " + role + " sur ce transfert.");
            case "REJET_TEMP_OUVERT":
                return ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Visa impossible : un rejet temporaire est encore ouvert. Résolvez-le d'abord.");
            case "VISA_PREALABLE_MANQUANT": {
                Role attendu = visaPrealableManquant(
                        decisionRepository.findByTransfertCredit_Id(transfert.getId()), role);
                return new ApiException(HttpStatus.CONFLICT.value(), ApiErrorCode.VISA_PREALABLE_MANQUANT,
                        "Le circuit est séquentiel : le visa " + attendu + " est attendu avant celui de " + role + ".");
            }
            default:
                return ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Visa impossible");
        }
    }

    /** Refuse le visa de ce rôle si le circuit ne le permet pas encore. */
    public void assertVisaPossible(TransfertCredit transfert, Role role) {
        String code = codeBlocageVisa(transfert, role);
        if (code != null) {
            throw exceptionBlocageVisa(code, transfert, role);
        }
    }

    /** État des quatre visas, du point de vue du rôle qui consulte. */
    @Transactional(readOnly = true)
    public List<VisaTransfertStatutDto> visaStatuts(Long transfertCreditId, AuthenticatedUser user) {
        TransfertCredit transfert = transfertRepository.findById(transfertCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Transfert de crédit non trouvé: " + transfertCreditId));
        List<DecisionTransfertCredit> decisions = decisionRepository.findByTransfertCredit_Id(transfertCreditId);
        Role moi = user != null ? user.getRole() : null;

        List<VisaTransfertStatutDto> statuts = new ArrayList<>();
        for (int i = 0; i < ORDRE_VISAS.size(); i++) {
            Role role = ORDRE_VISAS.get(i);
            DecisionTransfertCredit visa = decisions.stream()
                    .filter(d -> d.getRole() == role && d.getDecision() == DecisionCorrectionType.VISA)
                    .findFirst().orElse(null);

            String code = codeBlocageVisa(transfert, role);
            VisaTransfertStatutDto.VisaTransfertStatutDtoBuilder b = VisaTransfertStatutDto.builder()
                    .role(role)
                    .rang(i + 1)
                    .pose(visa != null)
                    .visaPrealableManquant(visaPrealableManquant(decisions, role))
                    .codeBlocage(code)
                    .motifBlocage(code == null ? null : exceptionBlocageVisa(code, transfert, role).getMessage())
                    // Seul le titulaire du rang peut viser : l'écran ne propose l'action qu'à lui.
                    .visablePourMoi(code == null && moi == role);
            if (visa != null) {
                b.decisionId(visa.getId())
                        .datePose(visa.getDateDecision())
                        .utilisateurId(visa.getUtilisateur() != null ? visa.getUtilisateur().getId() : null)
                        .utilisateurNom(visa.getUtilisateur() != null ? visa.getUtilisateur().getNomComplet() : null);
            }
            statuts.add(b.build());
        }
        return statuts;
    }

    /** Enregistre le visa d'un rôle du circuit, après contrôle du séquencement. */
    @Transactional
    public DecisionCreditDto enregistrerVisa(TransfertCredit transfert, Role role, AuthenticatedUser user) {
        assertVisaPossible(transfert, role);
        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        DecisionTransfertCredit entity = DecisionTransfertCredit.builder()
                .transfertCredit(transfert)
                .role(role)
                .build();
        entity.setDecision(DecisionCorrectionType.VISA);
        entity.setMotifRejet(null);
        entity.setDocumentsDemandes(new HashSet<>());
        entity.setRejetTempStatus(RejetTempStatus.RESOLU);
        entity.setRejetTempResolvedAt(Instant.now());
        entity.setDateDecision(Instant.now());
        entity.setUtilisateur(utilisateur);
        entity = decisionRepository.save(entity);

        // Le dossier reste en instruction tant que les quatre visas ne sont pas réunis.
        if (transfert.getStatut() == StatutTransfert.DEMANDE
                || transfert.getStatut() == StatutTransfert.A_RECONTROLER) {
            transfert.setStatut(StatutTransfert.EN_COURS);
            transfertRepository.save(transfert);
        }
        workflowNotificationHelper.transfert(transfert, WorkflowEventCode.TRANSFERT_EN_COURS, user);
        return toDto(entity);
    }

    /** {@code true} si les trois visas techniques précédant le Président sont réunis. */
    @Transactional(readOnly = true)
    public boolean visasTechniquesReunis(Long transfertCreditId) {
        List<DecisionTransfertCredit> decisions = decisionRepository.findByTransfertCredit_Id(transfertCreditId);
        return aVise(decisions, Role.DGD) && aVise(decisions, Role.DGI) && aVise(decisions, Role.DGTCP);
    }

    @Transactional(readOnly = true)
    public List<DecisionCreditDto> findByTransfert(Long transfertCreditId) {
        return decisionRepository.findByTransfertCredit_Id(transfertCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public DecisionCreditDto resolveRejetTemp(Long decisionId, AuthenticatedUser user) {
        if (decisionId == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Décision invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        DecisionTransfertCredit decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision transfert non trouvée: " + decisionId));

        if (decision.getDecision() != DecisionCorrectionType.REJET_TEMP || decision.getRejetTempStatus() != RejetTempStatus.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Résolution interdite: la décision n'est pas un REJET_TEMP OUVERT");
        }
        if (decision.getRole() == null || decision.getRole() != user.getRole()) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Résolution interdite: rôle non autorisé");
        }

        decision.setRejetTempStatus(RejetTempStatus.RESOLU);
        decision.setRejetTempResolvedAt(Instant.now());
        decision = decisionRepository.save(decision);

        Long transfertId = decision.getTransfertCredit() != null ? decision.getTransfertCredit().getId() : null;
        if (transfertId != null) {
            boolean stillOpen = !decisionRepository.findByTransfertCredit_IdAndDecisionAndRejetTempStatus(
                    transfertId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT
            ).isEmpty();

            if (!stillOpen) {
                TransfertCredit transfert = transfertRepository.findById(transfertId).orElse(null);
                if (transfert != null && transfert.getStatut() == StatutTransfert.INCOMPLETE) {
                    transfert.setStatut(StatutTransfert.A_RECONTROLER);
                    transfertRepository.save(transfert);
                    workflowNotificationHelper.transfert(transfert, WorkflowEventCode.TRANSFERT_REJET_TEMP_RESOLU, user);
                }
            }
        }

        return toDto(decision);
    }

    @Transactional
    public DecisionCreditDto saveDecision(Long transfertCreditId,
                                          DecisionCorrectionType decision,
                                          String motifRejet,
                                          Set<String> documentsDemandes,
                                          AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Role role = user.getRole();
        // Circuit P7 : les quatre rôles peuvent réclamer des compléments, pas seulement le Trésor.
        if (!ORDRE_VISAS.contains(role)) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Rôle non habilité à décider sur un transfert : " + role + ". Circuit attendu : " + ORDRE_VISAS);
        }
        if (decision != DecisionCorrectionType.REJET_TEMP) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Seul REJET_TEMP est pris en charge pour le transfert; la validation définitive se fait via POST .../valider");
        }
        if (motifRejet == null || motifRejet.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de rejet est obligatoire");
        }
        if (documentsDemandes == null || documentsDemandes.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La liste des documents demandés est obligatoire");
        }

        TransfertCredit transfert = transfertRepository.findById(transfertCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Transfert de crédit non trouvé: " + transfertCreditId));

        StatutTransfert st = transfert.getStatut();
        if (st == StatutTransfert.TRANSFERE || st == StatutTransfert.REJETE || st == StatutTransfert.ANNULEE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Décision interdite pour le statut: " + st);
        }

        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        DecisionTransfertCredit entity = DecisionTransfertCredit.builder()
                .transfertCredit(transfert)
                .role(role)
                .build();

        entity.setDecision(decision);
        entity.setMotifRejet(motifRejet);
        entity.setDocumentsDemandes(new HashSet<>(documentsDemandes));
        entity.setRejetTempStatus(RejetTempStatus.OUVERT);
        entity.setRejetTempResolvedAt(null);
        entity.setDateDecision(Instant.now());
        entity.setUtilisateur(utilisateur);

        entity = decisionRepository.save(entity);
        transfert.setStatut(StatutTransfert.INCOMPLETE);
        transfertRepository.save(transfert);
        workflowNotificationHelper.transfert(transfert, WorkflowEventCode.TRANSFERT_REJET_TEMP, user,
                entity.getId(), entity.getDocumentsDemandes());
        return toDto(entity);
    }

    @Transactional
    public List<RejetTempResponseDto> respondRejetTemp(
            Long decisionId,
            String message,
            MultipartFile file,
            String codeDocument,
            AuthenticatedUser user) throws IOException {
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le message de réponse est obligatoire");
        }
        if (file != null && !file.isEmpty()) {
            if (codeDocument == null || codeDocument.isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le type de document est obligatoire lors de l'envoi d'un fichier");
            }
            DecisionTransfertCredit decision = decisionRepository.findById(decisionId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision transfert non trouvée: " + decisionId));
            rejetTempResponseService.assertTransfertDecisionOpenRejetTemp(decision);
            if (decision.getDocumentsDemandes() == null || !decision.getDocumentsDemandes().contains(codeDocument)) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Le type de document doit être l'un des types demandés dans le rejet temporaire : "
                                + decision.getDocumentsDemandes());
            }
            Long transfertId = decision.getTransfertCredit().getId();
            documentTransfertCreditService.upload(transfertId, codeDocument, message, file, user, decisionId);
            return rejetTempResponseRepository.findByDecisionTransfertCredit_IdOrderByCreatedAtAsc(decisionId).stream()
                    .map(this::toResponseDto)
                    .collect(Collectors.toList());
        }
        return rejetTempResponseService.addResponseToTransfertDecision(decisionId, message, user);
    }

    private DecisionCreditDto toDto(DecisionTransfertCredit entity) {
        return DecisionCreditDto.builder()
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
                .rejetTempResponses(entity.getRejetTempResponses() != null
                        ? entity.getRejetTempResponses().stream().map(this::toResponseDto).collect(Collectors.toList())
                        : List.of())
                .build();
    }

    private RejetTempResponseDto toResponseDto(RejetTempResponse entity) {
        return RejetTempResponseDto.builder()
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
}
