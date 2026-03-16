package mr.gov.finances.sgci.workflow;

import org.springframework.stereotype.Component;

import mr.gov.finances.sgci.domain.enums.StatutUtilisation;

import static mr.gov.finances.sgci.domain.enums.StatutUtilisation.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Workflow Utilisation de Crédit (Processus 6-7).
 * DEMANDEE → EN_VERIFICATION → VISE → VALIDEE → LIQUIDEE/APUREE ou REJETEE
 */
@Component
public class UtilisationCreditWorkflow {

    private static final Map<StatutUtilisation, Set<StatutUtilisation>> TRANSITIONS = Map.ofEntries(
            Map.entry(DEMANDEE, EnumSet.of(EN_VERIFICATION, REJETEE)),
            Map.entry(EN_VERIFICATION, EnumSet.of(VISE, VALIDEE, REJETEE)),
            Map.entry(VISE, EnumSet.of(VALIDEE, LIQUIDEE, REJETEE)),
            Map.entry(VALIDEE, EnumSet.of(LIQUIDEE, APUREE)),
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
