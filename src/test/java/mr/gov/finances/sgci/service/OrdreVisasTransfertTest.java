package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.domain.entity.DecisionTransfertCredit;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.Role;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Séquencement du circuit P7 : DGD → DGI → DGTCP → Président.
 *
 * <p>Régression : le transfert pouvait s'exécuter sur le seul visa de la DGTCP, sans que la DGD
 * ni la DGI se soient prononcées. Ces cas verrouillent l'ordre imposé.
 */
class OrdreVisasTransfertTest {

    private final DecisionTransfertCreditService service =
            new DecisionTransfertCreditService(null, null, null, null, null, null, null);

    private static List<DecisionTransfertCredit> visasDe(Role... roles) {
        List<DecisionTransfertCredit> decisions = new ArrayList<>();
        for (Role r : roles) {
            DecisionTransfertCredit d = DecisionTransfertCredit.builder().role(r).build();
            d.setDecision(DecisionCorrectionType.VISA);
            decisions.add(d);
        }
        return decisions;
    }

    @Test
    void la_dgd_ouvre_le_circuit() {
        assertNull(service.visaPrealableManquant(visasDe(), Role.DGD),
                "la DGD vise en premier : rien ne la précède");
    }

    @Test
    void chaque_direction_attend_la_precedente() {
        assertEquals(Role.DGD, service.visaPrealableManquant(visasDe(), Role.DGI));
        assertEquals(Role.DGD, service.visaPrealableManquant(visasDe(), Role.DGTCP));
        assertEquals(Role.DGD, service.visaPrealableManquant(visasDe(), Role.PRESIDENT));

        assertEquals(Role.DGI, service.visaPrealableManquant(visasDe(Role.DGD), Role.DGTCP));
        assertEquals(Role.DGTCP, service.visaPrealableManquant(visasDe(Role.DGD, Role.DGI), Role.PRESIDENT));
    }

    @Test
    void le_visa_dgtcp_seul_ne_suffit_pas_au_president() {
        // Le défaut signalé : la DGTCP validait seule, sans DGD ni DGI.
        assertEquals(Role.DGD, service.visaPrealableManquant(visasDe(Role.DGTCP), Role.PRESIDENT),
                "le Président ne peut pas approuver sur le seul visa du Trésor");
    }

    @Test
    void le_president_tranche_une_fois_les_trois_visas_reunis() {
        assertNull(service.visaPrealableManquant(visasDe(Role.DGD, Role.DGI, Role.DGTCP), Role.PRESIDENT));
    }

    @Test
    void l_ordre_du_circuit_est_celui_du_cahier_des_charges() {
        assertEquals(List.of(Role.DGD, Role.DGI, Role.DGTCP, Role.PRESIDENT),
                DecisionTransfertCreditService.ORDRE_VISAS);
    }
}
