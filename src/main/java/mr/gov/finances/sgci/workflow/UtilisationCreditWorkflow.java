package mr.gov.finances.sgci.workflow;

import org.springframework.stereotype.Component;

import mr.gov.finances.sgci.domain.enums.StatutUtilisation;

import static mr.gov.finances.sgci.domain.enums.StatutUtilisation.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Workflow Utilisation de Crédit (Processus 6-7).
 * Rejet définitif (REJETEE) possible depuis tout statut actif sauf LIQUIDEE et APUREE.
 * Rejet temporaire (INCOMPLETE) est posé via les décisions REJET_TEMP, pas via ce graphe seul.
 */
@Component
public class UtilisationCreditWorkflow {

    private static final Map<StatutUtilisation, Set<StatutUtilisation>> TRANSITIONS = Map.ofEntries(
            Map.entry(BROUILLON, EnumSet.of(DEMANDEE)),
            Map.entry(DEMANDEE, EnumSet.of(INCOMPLETE, EN_VERIFICATION, REJETEE)),
            Map.entry(INCOMPLETE, EnumSet.of(A_RECONTROLER, REJETEE)),
            Map.entry(A_RECONTROLER, EnumSet.of(EN_VERIFICATION, REJETEE)),
            Map.entry(EN_VERIFICATION, EnumSet.of(INCOMPLETE, VISE, VALIDEE, REJETEE)),
            Map.entry(VISE, EnumSet.of(INCOMPLETE, VALIDEE, LIQUIDEE, REJETEE)),
            Map.entry(VALIDEE, EnumSet.of(LIQUIDEE, APUREE, REJETEE)),
            Map.entry(LIQUIDEE, EnumSet.noneOf(StatutUtilisation.class)),
            Map.entry(APUREE, EnumSet.noneOf(StatutUtilisation.class)),
            Map.entry(REJETEE, EnumSet.noneOf(StatutUtilisation.class))
    );

    public void validateTransition(StatutUtilisation from, StatutUtilisation to) {
        if (from == to) {
            throw new WorkflowTransitionException("Le statut est déjà: " + from);
        }
        Set<StatutUtilisation> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new WorkflowTransitionException(from.name(), to.name(), "UtilisationCredit");
        }
    }
}
