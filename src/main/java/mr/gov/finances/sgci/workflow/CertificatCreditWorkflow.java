package mr.gov.finances.sgci.workflow;

import org.springframework.stereotype.Component;

import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import static mr.gov.finances.sgci.domain.enums.StatutCertificat.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Workflow Certificat de Crédit (Processus 4-5, 8-11).
 * DEMANDE → EMIS → OUVERT → MODIFIE / CLOTURE / ANNULE
 */
@Component
public class CertificatCreditWorkflow {

    private static final Map<StatutCertificat, Set<StatutCertificat>> TRANSITIONS = Map.ofEntries(
            Map.entry(DEMANDE, EnumSet.of(EMIS, ANNULE)),
            Map.entry(EMIS, EnumSet.of(OUVERT, ANNULE)),
            Map.entry(OUVERT, EnumSet.of(MODIFIE, CLOTURE, ANNULE)),
            Map.entry(MODIFIE, EnumSet.of(OUVERT, CLOTURE, ANNULE)),
            Map.entry(CLOTURE, EnumSet.noneOf(StatutCertificat.class)),
            Map.entry(ANNULE, EnumSet.noneOf(StatutCertificat.class))
    );

    public void validateTransition(StatutCertificat from, StatutCertificat to) {
        if (from == to) {
            throw new WorkflowTransitionException("Le statut est déjà: " + from);
        }
        Set<StatutCertificat> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new WorkflowTransitionException(from.name(), to.name(), "CertificatCredit");
        }
    }
}
