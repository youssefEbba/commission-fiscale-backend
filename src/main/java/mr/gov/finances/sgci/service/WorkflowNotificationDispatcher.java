package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.WorkflowEventCode;
import mr.gov.finances.sgci.notification.WorkflowNotificationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Point d'entrée unique : notification in-app + e-mail pour chaque événement workflow.
 */
@Service
@RequiredArgsConstructor
public class WorkflowNotificationDispatcher {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final WorkflowRecipientResolver recipientResolver;
    private final NotificationNavigationHelper navigationHelper;

    public void dispatch(WorkflowEventCode event, WorkflowNotificationContext ctx) {
        if (event == null || ctx == null || ctx.getEntityType() == null || ctx.getEntityId() == null) {
            return;
        }
        List<Long> userIds = recipientResolver.resolve(ctx);
        if (userIds.isEmpty()) {
            return;
        }
        NotificationType type = mapNotificationType(event);
        String message = ctx.getMessageOverride() != null ? ctx.getMessageOverride() : buildMessage(event, ctx);
        Map<String, Object> payload = ctx.buildPayload(event.name(), navigationHelper);
        notificationService.notifyUsers(userIds, type, ctx.getEntityType(), ctx.getEntityId(), message, payload);

        String subject = buildSubject(event, ctx);
        emailService.sendToUsers(userIds, recipientResolver, subject, event.name(),
                ctx.getDossierLabel(), message, payload);
    }

    private NotificationType mapNotificationType(WorkflowEventCode event) {
        return switch (event) {
            case CORRECTION_SOUMISE, CORRECTION_STATUT_CHANGE, CORRECTION_ADOPTEE -> NotificationType.CORRECTION_STATUT_CHANGE;
            case CORRECTION_VISA, CORRECTION_REJET_DEFINITIF,
                 CORRECTION_RECLAMATION_DEPOSEE, CORRECTION_RECLAMATION_TRAITEE, CORRECTION_RECLAMATION_ANNULEE ->
                    NotificationType.CORRECTION_DECISION;
            case CORRECTION_REJET_TEMP -> NotificationType.REJET_TEMP_DECISION;
            case CORRECTION_REJET_TEMP_REPONSE -> NotificationType.REJET_TEMP_REPONSE;
            case CORRECTION_REJET_TEMP_RESOLU -> NotificationType.REJET_TEMP_RESOLU;

            case CERTIFICAT_SOUMIS, CERTIFICAT_STATUT_CHANGE, CERTIFICAT_PRISE_EN_CHARGE,
                 CERTIFICAT_PRESIDENT_VALIDATION, CERTIFICAT_OUVERT -> NotificationType.CERTIFICAT_STATUT_CHANGE;
            case CERTIFICAT_VISA -> NotificationType.CERTIFICAT_STATUT_CHANGE;
            case CERTIFICAT_REJET_TEMP -> NotificationType.REJET_TEMP_DECISION;
            case CERTIFICAT_REJET_TEMP_REPONSE -> NotificationType.REJET_TEMP_REPONSE;
            case CERTIFICAT_REJET_TEMP_RESOLU -> NotificationType.REJET_TEMP_RESOLU;

            case UTIL_DOUANE_SOUMISE, UTIL_DOUANE_STATUT_CHANGE, UTIL_DOUANE_VISA_DGD, UTIL_DOUANE_CHEQUE,
                 UTIL_DOUANE_TRESOR, UTIL_DOUANE_QUITTANCES, UTIL_DOUANE_LIQUIDEE, UTIL_DOUANE_CLOTUREE,
                 UTIL_DOUANE_REJET_DEFINITIF -> NotificationType.UTILISATION_STATUT_CHANGE;
            case UTIL_DOUANE_REJET_TEMP -> NotificationType.REJET_TEMP_DECISION;
            case UTIL_DOUANE_REJET_TEMP_REPONSE -> NotificationType.REJET_TEMP_REPONSE;
            case UTIL_DOUANE_REJET_TEMP_RESOLU -> NotificationType.REJET_TEMP_RESOLU;

            case UTIL_TVA_SOUMISE, UTIL_TVA_STATUT_CHANGE, UTIL_TVA_APUREE, UTIL_TVA_REJET_DEFINITIF ->
                    NotificationType.UTILISATION_STATUT_CHANGE;
            case UTIL_TVA_REJET_TEMP -> NotificationType.REJET_TEMP_DECISION;
            case UTIL_TVA_REJET_TEMP_REPONSE -> NotificationType.REJET_TEMP_REPONSE;
            case UTIL_TVA_REJET_TEMP_RESOLU -> NotificationType.REJET_TEMP_RESOLU;

            case TRANSFERT_DEMANDE, TRANSFERT_EN_COURS, TRANSFERT_VALIDE, TRANSFERT_REJETE, TRANSFERT_ANNULE ->
                    NotificationType.TRANSFERT_CREDIT;
            case TRANSFERT_REJET_TEMP -> NotificationType.REJET_TEMP_DECISION;
            case TRANSFERT_REJET_TEMP_REPONSE -> NotificationType.REJET_TEMP_REPONSE;
            case TRANSFERT_REJET_TEMP_RESOLU -> NotificationType.REJET_TEMP_RESOLU;

            case CLOTURE_PROPOSEE, CLOTURE_APPROUVEE, CLOTURE_REJETEE, CLOTURE_FINALISEE ->
                    NotificationType.CLOTURE_CERTIFICAT;
        };
    }

    private String buildMessage(WorkflowEventCode event, WorkflowNotificationContext ctx) {
        String dossier = ctx.getDossierLabel() != null ? ctx.getDossierLabel() : ("#" + ctx.getEntityId());
        return switch (event) {
            case CORRECTION_SOUMISE -> "Demande de correction " + dossier + " soumise";
            case CORRECTION_STATUT_CHANGE -> "Demande de correction " + dossier + " — statut : " + label(ctx.getNewStatus());
            case CORRECTION_VISA -> "Visa posé sur la demande de correction " + dossier;
            case CORRECTION_REJET_TEMP -> "Rejet temporaire sur la demande de correction " + dossier;
            case CORRECTION_REJET_DEFINITIF -> "Rejet définitif de la demande de correction " + dossier;
            case CORRECTION_REJET_TEMP_REPONSE -> "Réponse au rejet temporaire — correction " + dossier;
            case CORRECTION_REJET_TEMP_RESOLU -> "Rejet temporaire résolu — correction " + dossier;
            case CORRECTION_ADOPTEE -> "Demande de correction " + dossier + " adoptée";
            case CORRECTION_RECLAMATION_DEPOSEE -> "Réclamation déposée — correction " + dossier;
            case CORRECTION_RECLAMATION_TRAITEE -> "Réclamation traitée — correction " + dossier;
            case CORRECTION_RECLAMATION_ANNULEE -> "Réclamation annulée — correction " + dossier;

            case CERTIFICAT_SOUMIS -> "Certificat " + dossier + " soumis";
            case CERTIFICAT_STATUT_CHANGE, CERTIFICAT_PRISE_EN_CHARGE, CERTIFICAT_OUVERT ->
                    "Certificat " + dossier + " — statut : " + label(ctx.getNewStatus());
            case CERTIFICAT_VISA -> "Visa posé sur le certificat " + dossier;
            case CERTIFICAT_REJET_TEMP -> "Rejet temporaire sur le certificat " + dossier;
            case CERTIFICAT_REJET_TEMP_REPONSE -> "Réponse au rejet temporaire — certificat " + dossier;
            case CERTIFICAT_REJET_TEMP_RESOLU -> "Rejet temporaire résolu — certificat " + dossier;
            case CERTIFICAT_PRESIDENT_VALIDATION -> "Certificat " + dossier + " en attente de validation Président";

            case UTIL_DOUANE_SOUMISE -> "Demande d'utilisation douane " + dossier + " soumise";
            case UTIL_DOUANE_STATUT_CHANGE, UTIL_DOUANE_VISA_DGD, UTIL_DOUANE_CHEQUE, UTIL_DOUANE_TRESOR,
                 UTIL_DOUANE_QUITTANCES, UTIL_DOUANE_LIQUIDEE, UTIL_DOUANE_CLOTUREE ->
                    "Utilisation douane " + dossier + " — statut : " + label(ctx.getNewStatus());
            case UTIL_DOUANE_REJET_TEMP -> "Rejet temporaire — utilisation douane " + dossier;
            case UTIL_DOUANE_REJET_TEMP_REPONSE -> "Réponse rejet temporaire — utilisation douane " + dossier;
            case UTIL_DOUANE_REJET_TEMP_RESOLU -> "Rejet temporaire résolu — utilisation douane " + dossier;
            case UTIL_DOUANE_REJET_DEFINITIF -> "Rejet définitif — utilisation douane " + dossier;

            case UTIL_TVA_SOUMISE -> "Demande d'utilisation TVA " + dossier + " soumise";
            case UTIL_TVA_STATUT_CHANGE, UTIL_TVA_APUREE ->
                    "Utilisation TVA " + dossier + " — statut : " + label(ctx.getNewStatus());
            case UTIL_TVA_REJET_TEMP -> "Rejet temporaire — utilisation TVA " + dossier;
            case UTIL_TVA_REJET_TEMP_REPONSE -> "Réponse rejet temporaire — utilisation TVA " + dossier;
            case UTIL_TVA_REJET_TEMP_RESOLU -> "Rejet temporaire résolu — utilisation TVA " + dossier;
            case UTIL_TVA_REJET_DEFINITIF -> "Rejet définitif — utilisation TVA " + dossier;

            case TRANSFERT_DEMANDE -> "Nouvelle demande de transfert de crédit " + dossier;
            case TRANSFERT_EN_COURS -> "Transfert " + dossier + " — pièces déposées (EN_COURS)";
            case TRANSFERT_REJET_TEMP -> "Rejet temporaire — transfert " + dossier;
            case TRANSFERT_REJET_TEMP_REPONSE -> "Réponse rejet temporaire — transfert " + dossier;
            case TRANSFERT_REJET_TEMP_RESOLU -> "Rejet temporaire résolu — transfert " + dossier;
            case TRANSFERT_VALIDE -> "Transfert de crédit " + dossier + " validé";
            case TRANSFERT_REJETE -> "Transfert de crédit " + dossier + " rejeté";
            case TRANSFERT_ANNULE -> "Transfert de crédit " + dossier + " annulé";

            case CLOTURE_PROPOSEE -> "Proposition de clôture — certificat " + dossier;
            case CLOTURE_APPROUVEE -> "Clôture approuvée par le Président — certificat " + dossier;
            case CLOTURE_REJETEE -> "Proposition de clôture rejetée — certificat " + dossier;
            case CLOTURE_FINALISEE -> "Clôture finalisée — certificat " + dossier;
        };
    }

    private String buildSubject(WorkflowEventCode event, WorkflowNotificationContext ctx) {
        return "[SGCI] " + buildMessage(event, ctx);
    }

    private static String label(String s) {
        return s != null ? s : "—";
    }
}
