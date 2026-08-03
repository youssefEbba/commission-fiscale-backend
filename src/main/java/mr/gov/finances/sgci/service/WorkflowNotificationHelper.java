package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.ClotureCredit;
import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.entity.DecisionUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.domain.enums.WorkflowEventCode;
import mr.gov.finances.sgci.notification.WorkflowNotificationContext;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowNotificationHelper {

    private final WorkflowNotificationDispatcher dispatcher;
    private final VisaRequirementResolver visaRequirementResolver;

    public WorkflowNotificationHelper(WorkflowNotificationDispatcher dispatcher,
                                      VisaRequirementResolver visaRequirementResolver) {
        this.dispatcher = dispatcher;
        this.visaRequirementResolver = visaRequirementResolver;
    }

    public void correctionStatut(DemandeCorrection d, StatutDemande statut, AuthenticatedUser actor,
                                 String motif, boolean finale) {
        if (d == null) {
            return;
        }
        WorkflowEventCode event = WorkflowEventCode.CORRECTION_STATUT_CHANGE;
        if (statut == StatutDemande.RECUE) {
            event = WorkflowEventCode.CORRECTION_SOUMISE;
        } else if (finale && statut == StatutDemande.ADOPTEE) {
            event = WorkflowEventCode.CORRECTION_ADOPTEE;
        } else if (statut == StatutDemande.REJETEE) {
            event = WorkflowEventCode.CORRECTION_REJET_DEFINITIF;
        }
        dispatchCorrection(event, d, actor, statut != null ? statut.name() : null, motif, true);
    }

    public void correctionDecision(DemandeCorrection d, DecisionCorrection decision, AuthenticatedUser actor) {
        if (d == null || decision == null || decision.getDecision() == null) {
            return;
        }
        String decisionType = decision.getDecision().name();
        WorkflowEventCode event = "REJET_TEMP".equals(decisionType)
                ? WorkflowEventCode.CORRECTION_REJET_TEMP
                : ("REJET".equals(decisionType) ? WorkflowEventCode.CORRECTION_REJET_DEFINITIF : WorkflowEventCode.CORRECTION_VISA);
        if ("REJET_TEMP".equals(decisionType)) {
            Map<String, Object> extra = rejetTempExtra(decision.getId(), decision.getDocumentsDemandes());
            dispatchCorrection(event, d, actor, StatutDemande.INCOMPLETE.name(), decision.getMotifRejet(), false, extra, List.of());
        } else {
            dispatchCorrection(event, d, actor, null, decision.getMotifRejet(), false);
        }
    }

    public void correctionDecision(DemandeCorrection d, String decisionType, AuthenticatedUser actor, String motif) {
        if (d == null) {
            return;
        }
        WorkflowEventCode event = "REJET_TEMP".equals(decisionType)
                ? WorkflowEventCode.CORRECTION_REJET_TEMP
                : ("REJET".equals(decisionType) ? WorkflowEventCode.CORRECTION_REJET_DEFINITIF : WorkflowEventCode.CORRECTION_VISA);
        dispatchCorrection(event, d, actor, null, motif, false);
    }

    public void correctionRejetTempResolu(DemandeCorrection d, AuthenticatedUser actor) {
        dispatchCorrection(WorkflowEventCode.CORRECTION_REJET_TEMP_RESOLU, d, actor, StatutDemande.RECEVABLE.name(), null, false);
    }

    public void correctionRejetTempReponse(DemandeCorrection d, AuthenticatedUser actor, Role emitterRole,
                                           String codeDocument, Long decisionId) {
        Map<String, Object> extra = new HashMap<>();
        if (codeDocument != null) {
            extra.put("codeDocument", codeDocument);
        }
        if (decisionId != null) {
            extra.put("decisionId", decisionId);
        }
        WorkflowNotificationContext ctx = baseCorrection(d, actor)
                .extraPayload(extra)
                .roleRecipients(emitterRole != null ? List.of(emitterRole) : List.of(Role.DGD, Role.DGTCP, Role.DGI, Role.DGB))
                .build();
        dispatcher.dispatch(WorkflowEventCode.CORRECTION_REJET_TEMP_REPONSE, ctx);
    }

    public void correctionReclamation(DemandeCorrection d, WorkflowEventCode event, AuthenticatedUser actor) {
        if (d == null) {
            return;
        }
        List<Role> roles = switch (event) {
            case CORRECTION_RECLAMATION_DEPOSEE, CORRECTION_RECLAMATION_ANNULEE ->
                    List.of(Role.DGTCP, Role.PRESIDENT);
            case CORRECTION_RECLAMATION_TRAITEE -> List.of();
            default -> List.of();
        };
        WorkflowNotificationContext ctx = baseCorrection(d, actor)
                .roleRecipients(roles)
                .build();
        if (event == WorkflowEventCode.CORRECTION_RECLAMATION_TRAITEE) {
            ctx = baseCorrection(d, actor).build();
        }
        dispatcher.dispatch(event, ctx);
    }

    private void dispatchCorrection(WorkflowEventCode event, DemandeCorrection d, AuthenticatedUser actor,
                                    String newStatus, String motif, boolean includeCommission) {
        dispatchCorrection(event, d, actor, newStatus, motif, includeCommission, Map.of(), commissionRoles(includeCommission, d));
    }

    private void dispatchCorrection(WorkflowEventCode event, DemandeCorrection d, AuthenticatedUser actor,
                                    String newStatus, String motif, boolean includeCommission,
                                    Map<String, Object> extra, List<Role> extraRoles) {
        WorkflowNotificationContext.WorkflowNotificationContextBuilder b = baseCorrection(d, actor)
                .newStatus(newStatus)
                .motif(motif)
                .extraPayload(extra);
        if (includeCommission) {
            b.roleRecipients(commissionRoles(true, d));
        } else if (extraRoles != null && !extraRoles.isEmpty()) {
            b.roleRecipients(extraRoles);
        }
        dispatcher.dispatch(event, b.build());
    }

    private WorkflowNotificationContext.WorkflowNotificationContextBuilder baseCorrection(DemandeCorrection d, AuthenticatedUser actor) {
        return WorkflowNotificationContext.builder()
                .entityType("DemandeCorrection")
                .entityId(d.getId())
                .dossierLabel(ref(d.getReference(), d.getNumero()))
                .actor(actor)
                .entrepriseId(d.getEntreprise() != null ? d.getEntreprise().getId() : null)
                .autoriteContractanteId(d.getAutoriteContractante() != null ? d.getAutoriteContractante().getId() : null);
    }

    public void certificatStatut(CertificatCredit c, String statut, AuthenticatedUser actor, WorkflowEventCode eventOverride) {
        if (c == null) {
            return;
        }
        WorkflowEventCode event = eventOverride != null ? eventOverride : WorkflowEventCode.CERTIFICAT_STATUT_CHANGE;
        if ("ENVOYEE".equals(statut)) {
            event = WorkflowEventCode.CERTIFICAT_SOUMIS;
        }
        List<Role> roles = "ENVOYEE".equals(statut) || "EN_CONTROLE".equals(statut)
                ? new ArrayList<>(visaRequirementResolver.requiredRolesForCertificat(c)) : List.of();
        dispatcher.dispatch(event, WorkflowNotificationContext.builder()
                .entityType("CertificatCredit")
                .entityId(c.getId())
                .dossierLabel(ref(c.getReference(), c.getNumero()))
                .actor(actor)
                .newStatus(statut)
                .entrepriseId(c.getEntreprise() != null ? c.getEntreprise().getId() : null)
                .roleRecipients(roles)
                .build());
    }

    public void certificatDecision(CertificatCredit c, String decisionType, AuthenticatedUser actor, String motif,
                                   Set<String> documentsDemandes, Long decisionId) {
        if (c == null) {
            return;
        }
        WorkflowEventCode event = "REJET_TEMP".equals(decisionType)
                ? WorkflowEventCode.CERTIFICAT_REJET_TEMP
                : WorkflowEventCode.CERTIFICAT_VISA;
        WorkflowNotificationContext.WorkflowNotificationContextBuilder builder = baseCertificat(c, actor).motif(motif);
        if ("REJET_TEMP".equals(decisionType)) {
            Map<String, Object> extra = rejetTempExtra(decisionId, documentsDemandes);
            builder.newStatus("INCOMPLETE").extraPayload(extra);
        }
        dispatcher.dispatch(event, builder.build());
    }

    public void certificatPresidentValidation(CertificatCredit c) {
        if (c == null) {
            return;
        }
        dispatcher.dispatch(WorkflowEventCode.CERTIFICAT_PRESIDENT_VALIDATION, WorkflowNotificationContext.builder()
                .entityType("CertificatCredit")
                .entityId(c.getId())
                .dossierLabel(ref(c.getReference(), c.getNumero()))
                .newStatus("EN_VALIDATION_PRESIDENT")
                .roleRecipients(List.of(Role.PRESIDENT))
                .build());
    }

    public void certificatRejetTempResolu(CertificatCredit c, AuthenticatedUser actor) {
        dispatcher.dispatch(WorkflowEventCode.CERTIFICAT_REJET_TEMP_RESOLU, baseCertificat(c, actor)
                .newStatus("A_RECONTROLER")
                .build());
    }

    public void certificatRejetTempReponse(CertificatCredit c, AuthenticatedUser actor, Role emitterRole,
                                           String codeDocument, Long decisionId) {
        Map<String, Object> extra = new HashMap<>();
        if (codeDocument != null && !codeDocument.isBlank()) {
            extra.put("codeDocument", codeDocument);
        }
        if (decisionId != null) {
            extra.put("decisionId", decisionId);
        }
        dispatcher.dispatch(WorkflowEventCode.CERTIFICAT_REJET_TEMP_REPONSE, baseCertificat(c, actor)
                .roleRecipients(emitterRole != null ? List.of(emitterRole) : List.of(Role.DGI, Role.DGD, Role.DGTCP))
                .extraPayload(extra)
                .build());
    }

    public void utilisationStatut(UtilisationCredit u, String statut, AuthenticatedUser actor, WorkflowEventCode eventOverride) {
        if (u == null) {
            return;
        }
        boolean douane = u.getType() == TypeUtilisation.DOUANIER;
        WorkflowEventCode event = eventOverride != null ? eventOverride : mapUtilStatutEvent(douane, statut);
        List<Role> roles = resolveUtilRoles(douane, statut);
        dispatcher.dispatch(event, WorkflowNotificationContext.builder()
                .entityType("UtilisationCredit")
                .entityId(u.getId())
                .dossierLabel(utilisationLabel(u))
                .actor(actor)
                .newStatus(statut)
                .entrepriseId(u.getEntreprise() != null ? u.getEntreprise().getId() : null)
                .roleRecipients(roles)
                .build());
    }

    public void utilisationVisa(UtilisationCredit u, AuthenticatedUser actor, Role visaRole) {
        if (u == null) {
            return;
        }
        boolean douane = u.getType() == TypeUtilisation.DOUANIER;
        WorkflowEventCode event = douane
                ? WorkflowEventCode.UTIL_DOUANE_STATUT_CHANGE
                : WorkflowEventCode.UTIL_TVA_STATUT_CHANGE;
        String role = visaRole != null ? visaRole.name()
                : (actor != null && actor.getRole() != null ? actor.getRole().name() : "");
        dispatcher.dispatch(event, WorkflowNotificationContext.builder()
                .entityType("UtilisationCredit")
                .entityId(u.getId())
                .dossierLabel(utilisationLabel(u))
                .actor(actor)
                .messageOverride("Visa " + role + " posé sur l'utilisation " + utilisationLabel(u))
                .entrepriseId(u.getEntreprise() != null ? u.getEntreprise().getId() : null)
                .build());
    }

    public void utilisationRejetTemp(UtilisationCredit u, DecisionUtilisationCredit decision, AuthenticatedUser actor) {
        if (u == null || decision == null) {
            return;
        }
        boolean douane = u.getType() == TypeUtilisation.DOUANIER;
        Map<String, Object> extra = rejetTempExtra(decision.getId(), decision.getDocumentsDemandes());
        dispatcher.dispatch(douane ? WorkflowEventCode.UTIL_DOUANE_REJET_TEMP : WorkflowEventCode.UTIL_TVA_REJET_TEMP,
                WorkflowNotificationContext.builder()
                        .entityType("UtilisationCredit")
                        .entityId(u.getId())
                        .dossierLabel(utilisationLabel(u))
                        .actor(actor)
                        .motif(decision.getMotifRejet())
                        .newStatus("INCOMPLETE")
                        .entrepriseId(u.getEntreprise() != null ? u.getEntreprise().getId() : null)
                        .extraPayload(extra)
                        .build());
    }

    public void utilisationRejetTempResolu(UtilisationCredit u, AuthenticatedUser actor) {
        boolean douane = u.getType() == TypeUtilisation.DOUANIER;
        dispatcher.dispatch(douane ? WorkflowEventCode.UTIL_DOUANE_REJET_TEMP_RESOLU : WorkflowEventCode.UTIL_TVA_REJET_TEMP_RESOLU,
                WorkflowNotificationContext.builder()
                        .entityType("UtilisationCredit")
                        .entityId(u.getId())
                        .dossierLabel(utilisationLabel(u))
                        .actor(actor)
                        .newStatus("A_RECONTROLER")
                        .entrepriseId(u.getEntreprise() != null ? u.getEntreprise().getId() : null)
                        .roleRecipients(douane ? List.of(Role.DGD) : List.of(Role.DGTCP))
                        .build());
    }

    public void utilisationRejetTempReponse(UtilisationCredit u, AuthenticatedUser actor, Role emitterRole, Long decisionId) {
        boolean douane = u.getType() == TypeUtilisation.DOUANIER;
        Map<String, Object> extra = new HashMap<>();
        if (decisionId != null) {
            extra.put("decisionId", decisionId);
        }
        dispatcher.dispatch(douane ? WorkflowEventCode.UTIL_DOUANE_REJET_TEMP_REPONSE : WorkflowEventCode.UTIL_TVA_REJET_TEMP_REPONSE,
                WorkflowNotificationContext.builder()
                        .entityType("UtilisationCredit")
                        .entityId(u.getId())
                        .dossierLabel(utilisationLabel(u))
                        .actor(actor)
                        .roleRecipients(emitterRole != null ? List.of(emitterRole) : List.of(Role.DGTCP, Role.DGD))
                        .extraPayload(extra)
                        .build());
    }

    public void transfert(TransfertCredit t, WorkflowEventCode event, AuthenticatedUser actor) {
        transfert(t, event, actor, null, null);
    }

    public void transfert(TransfertCredit t, WorkflowEventCode event, AuthenticatedUser actor,
                          Long decisionId, Set<String> documentsDemandes) {
        if (t == null) {
            return;
        }
        String label = t.getCertificatCredit() != null
                ? ref(t.getCertificatCredit().getReference(), t.getCertificatCredit().getNumero())
                : ("#" + t.getId());
        Long entrepriseId = t.getCertificatCredit() != null && t.getCertificatCredit().getEntreprise() != null
                ? t.getCertificatCredit().getEntreprise().getId() : null;
        List<Role> roles = switch (event) {
            case TRANSFERT_DEMANDE, TRANSFERT_EN_COURS, TRANSFERT_ANNULE -> List.of(Role.DGTCP);
            case TRANSFERT_REJET_TEMP_RESOLU -> List.of();
            case TRANSFERT_REJET_TEMP_REPONSE -> List.of(Role.DGTCP, Role.PRESIDENT);
            case TRANSFERT_REJET_TEMP, TRANSFERT_VALIDE, TRANSFERT_REJETE -> List.of();
            default -> List.of(Role.DGTCP);
        };
        Map<String, Object> extra = rejetTempExtra(decisionId, documentsDemandes);
        dispatcher.dispatch(event, WorkflowNotificationContext.builder()
                .entityType("TransfertCredit")
                .entityId(t.getId())
                .dossierLabel(label)
                .actor(actor)
                .newStatus(t.getStatut() != null ? t.getStatut().name() : null)
                .entrepriseId(entrepriseId)
                .roleRecipients(roles)
                .extraPayload(extra)
                .build());
    }

    public void cloture(ClotureCredit cc, WorkflowEventCode event, AuthenticatedUser actor) {
        if (cc == null || cc.getCertificatCredit() == null) {
            return;
        }
        CertificatCredit c = cc.getCertificatCredit();
        List<Role> roles = switch (event) {
            case CLOTURE_PROPOSEE -> List.of(Role.PRESIDENT);
            case CLOTURE_APPROUVEE, CLOTURE_REJETEE -> List.of(Role.DGTCP);
            case CLOTURE_FINALISEE -> List.of(Role.DGTCP);
            default -> List.of();
        };
        // Étapes proposition/approbation/rejet : internes (DGTCP ↔ Président).
        // L'entreprise n'est informée qu'à la finalisation de la clôture.
        Long entrepriseId = event == WorkflowEventCode.CLOTURE_FINALISEE && c.getEntreprise() != null
                ? c.getEntreprise().getId()
                : null;
        dispatcher.dispatch(event, WorkflowNotificationContext.builder()
                .entityType("ClotureCredit")
                .entityId(cc.getId())
                .certificatCreditId(c.getId())
                .dossierLabel(ref(c.getReference(), c.getNumero()))
                .actor(actor)
                .entrepriseId(entrepriseId)
                .roleRecipients(roles)
                .motif(cc.getMotif() != null ? cc.getMotif().name() : null)
                .build());
    }

    private WorkflowNotificationContext.WorkflowNotificationContextBuilder baseCertificat(CertificatCredit c,
                                                                                          AuthenticatedUser actor) {
        Long autoriteId = c.getDemandeCorrection() != null && c.getDemandeCorrection().getAutoriteContractante() != null
                ? c.getDemandeCorrection().getAutoriteContractante().getId()
                : null;
        return WorkflowNotificationContext.builder()
                .entityType("CertificatCredit")
                .entityId(c.getId())
                .dossierLabel(ref(c.getReference(), c.getNumero()))
                .actor(actor)
                .entrepriseId(c.getEntreprise() != null ? c.getEntreprise().getId() : null)
                .autoriteContractanteId(autoriteId);
    }

    private static Map<String, Object> rejetTempExtra(Long decisionId, Set<String> documentsDemandes) {
        Map<String, Object> extra = new HashMap<>();
        if (decisionId != null) {
            extra.put("decisionId", decisionId);
        }
        if (documentsDemandes != null && !documentsDemandes.isEmpty()) {
            extra.put("documentsDemandes", new ArrayList<>(documentsDemandes));
        }
        return extra;
    }

    /**
     * Membres de la commission à notifier. La DGD (crédit extérieur) et la DGI (crédit intérieur) sont
     * filtrées lorsque leur enveloppe est nulle, afin de ne pas générer de « tâche fantôme ».
     */
    private List<Role> commissionRoles(boolean include, DemandeCorrection d) {
        if (!include) {
            return List.of();
        }
        Set<Role> required = visaRequirementResolver.requiredRolesForCorrection(d);
        List<Role> roles = new ArrayList<>();
        roles.add(Role.PRESIDENT);
        roles.addAll(required);
        return roles;
    }

    private String utilisationLabel(UtilisationCredit u) {
        if (u.getReference() != null && !u.getReference().isBlank()) {
            return u.getReference();
        }
        if (u.getCertificatCredit() != null && u.getCertificatCredit().getNumero() != null) {
            return u.getCertificatCredit().getNumero() + " / util #" + u.getId();
        }
        return "utilisation #" + u.getId();
    }

    /** Référence lisible si disponible, sinon repli sur le numéro interne — cohérent avec displayRef() côté front. */
    private static String ref(String reference, String numero) {
        return reference != null && !reference.isBlank() ? reference : numero;
    }

    private WorkflowEventCode mapUtilStatutEvent(boolean douane, String statut) {
        if (statut == null) {
            return douane ? WorkflowEventCode.UTIL_DOUANE_STATUT_CHANGE : WorkflowEventCode.UTIL_TVA_STATUT_CHANGE;
        }
        if (douane) {
            return switch (statut) {
                case "DEMANDEE" -> WorkflowEventCode.UTIL_DOUANE_SOUMISE;
                case "EN_CONTROLE_DGD", "VISE" -> WorkflowEventCode.UTIL_DOUANE_VISA_DGD;
                case "CHEQUE_SAISI" -> WorkflowEventCode.UTIL_DOUANE_CHEQUE;
                case "ENVOYEE_AU_TRESOR" -> WorkflowEventCode.UTIL_DOUANE_TRESOR;
                case "QUITTANCES_ENREGISTREES" -> WorkflowEventCode.UTIL_DOUANE_QUITTANCES;
                case "LIQUIDEE" -> WorkflowEventCode.UTIL_DOUANE_LIQUIDEE;
                case "CLOTUREE" -> WorkflowEventCode.UTIL_DOUANE_CLOTUREE;
                case "REJETEE" -> WorkflowEventCode.UTIL_DOUANE_REJET_DEFINITIF;
                default -> WorkflowEventCode.UTIL_DOUANE_STATUT_CHANGE;
            };
        }
        return switch (statut) {
            case "DEMANDEE" -> WorkflowEventCode.UTIL_TVA_SOUMISE;
            case "APUREE" -> WorkflowEventCode.UTIL_TVA_APUREE;
            case "REJETEE" -> WorkflowEventCode.UTIL_TVA_REJET_DEFINITIF;
            default -> WorkflowEventCode.UTIL_TVA_STATUT_CHANGE;
        };
    }

    private List<Role> resolveUtilRoles(boolean douane, String statut) {
        if (statut == null) {
            return List.of();
        }
        if (douane) {
            return switch (statut) {
                case "DEMANDEE", "EN_CONTROLE_DGD", "VISE" -> List.of(Role.DGD);
                case "CHEQUE_SAISI", "ENVOYEE_AU_TRESOR", "QUITTANCES_ENREGISTREES", "LIQUIDEE", "CLOTUREE" ->
                        List.of(Role.DGTCP);
                case "REJETEE" -> List.of(Role.DGD, Role.DGTCP);
                default -> List.of();
            };
        }
        return switch (statut) {
            case "DEMANDEE", "EN_VERIFICATION", "VALIDEE", "APUREE", "REJETEE", "INCOMPLETE", "A_RECONTROLER" ->
                    List.of(Role.DGTCP);
            default -> List.of();
        };
    }
}
