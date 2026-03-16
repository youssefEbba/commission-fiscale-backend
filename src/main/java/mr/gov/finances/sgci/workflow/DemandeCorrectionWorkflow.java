package mr.gov.finances.sgci.workflow;

import org.springframework.stereotype.Component;

import mr.gov.finances.sgci.domain.enums.StatutDemande;

import static mr.gov.finances.sgci.domain.enums.StatutDemande.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Workflow Demande de Correction (Processus 1-3).
 * RECUE → RECEVABLE/INCOMPLETE/REJETEE → EN_EVALUATION → EN_VALIDATION → ADOPTEE/REJETEE → NOTIFIEE
 */
@Component
public class DemandeCorrectionWorkflow {

    private static final Map<StatutDemande, Set<StatutDemande>> TRANSITIONS = Map.ofEntries(
            Map.entry(RECUE, EnumSet.of(RECEVABLE, INCOMPLETE, REJETEE, EN_EVALUATION)),
            Map.entry(INCOMPLETE, EnumSet.of(RECEVABLE, REJETEE)),
            Map.entry(RECEVABLE, EnumSet.of(EN_EVALUATION)),
            Map.entry(EN_EVALUATION, EnumSet.of(EN_VALIDATION, ADOPTEE)),
            Map.entry(EN_VALIDATION, EnumSet.of(ADOPTEE, REJETEE)),
            Map.entry(ADOPTEE, EnumSet.of(NOTIFIEE)),
            Map.entry(REJETEE, EnumSet.noneOf(StatutDemande.class)),
            Map.entry(NOTIFIEE, EnumSet.noneOf(StatutDemande.class))
    );

    public void validateTransition(StatutDemande from, StatutDemande to) {
        if (from == to) {
            throw new WorkflowTransitionException("Le statut est déjà: " + from);
        }
        Set<StatutDemande> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new WorkflowTransitionException(from.name(), to.name(), "DemandeCorrection");
        }
    }
}
