package mr.gov.finances.sgci.workflow;

import org.springframework.stereotype.Component;

import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import static mr.gov.finances.sgci.domain.enums.StatutCertificat.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Workflow Certificat de Crédit — mise en place.
 *
 * BROUILLON ──► ENVOYEE ──(prise en charge acteur)──► EN_CONTROLE ──(3 visas DGI+DGD+DGTCP)──► EN_VALIDATION_PRESIDENT ──► VALIDE_PRESIDENT
 *     │  ▲                                          │                          │
 *     ▼  │                                          │                          │
 * INCOMPLETE ──► A_RECONTROLER                      │                          │
 *                                                   ▼                          ▼
 *                                             (DGTCP direct)          EN_OUVERTURE_DGTCP
 *                                                   │                          │
 *                                                   └──────────► OUVERT ◄──────┘
 */
@Component
public class CertificatCreditWorkflow {

    private static final Map<StatutCertificat, Set<StatutCertificat>> TRANSITIONS = Map.ofEntries(
            Map.entry(BROUILLON,                EnumSet.of(ENVOYEE, ANNULE)),
            Map.entry(ENVOYEE,                  EnumSet.of(EN_CONTROLE, ANNULE)),
            Map.entry(EN_CONTROLE,              EnumSet.of(INCOMPLETE, EN_VALIDATION_PRESIDENT, ANNULE)),
            Map.entry(INCOMPLETE,               EnumSet.of(A_RECONTROLER, ANNULE)),
            Map.entry(A_RECONTROLER,            EnumSet.of(EN_CONTROLE, ANNULE)),
            Map.entry(EN_VALIDATION_PRESIDENT,  EnumSet.of(VALIDE_PRESIDENT, OUVERT, ANNULE)),
            Map.entry(VALIDE_PRESIDENT,         EnumSet.of(EN_OUVERTURE_DGTCP, OUVERT, ANNULE)),
            Map.entry(EN_OUVERTURE_DGTCP,       EnumSet.of(OUVERT, ANNULE)),
            Map.entry(OUVERT,                   EnumSet.of(MODIFIE, CLOTURE, ANNULE)),
            Map.entry(MODIFIE,                  EnumSet.of(OUVERT, CLOTURE, ANNULE)),
            Map.entry(CLOTURE,                  EnumSet.noneOf(StatutCertificat.class)),
            Map.entry(ANNULE,                   EnumSet.noneOf(StatutCertificat.class))
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
