package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Avenant;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.StatutAvenant;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.AvenantRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fait suivre à l'avenant financier le sort de sa demande de correction.
 *
 * <p>L'avenant n'est adopté qu'avec sa correction : adoptée, il passe {@code ADOPTE} et la DGTCP
 * peut l'appliquer ; rejetée ou annulée, il passe {@code REJETE}. Composant à part, qui ne dépend que
 * du dépôt des avenants : {@code DemandeCorrectionService} l'appelle sans former de cycle avec
 * {@code AvenantService}.
 */
@Component
@RequiredArgsConstructor
public class AvenantSuiviCorrection {

    private final AvenantRepository avenantRepository;
    private final AuditService auditService;

    public void apresDecision(DemandeCorrection demande) {
        if (demande == null || demande.getId() == null) {
            return;
        }
        StatutDemande s = demande.getStatut();
        if (s != StatutDemande.ADOPTEE && s != StatutDemande.REJETEE && s != StatutDemande.ANNULEE) {
            return;
        }
        for (Avenant a : avenantRepository.findByDemandeCorrectionId(demande.getId())) {
            if (a.getStatut() != StatutAvenant.EN_COURS_TRAITEMENT) {
                continue;
            }
            if (s == StatutDemande.ADOPTEE) {
                a.setStatut(StatutAvenant.ADOPTE);
            } else {
                a.setStatut(StatutAvenant.REJETE);
                a.setMotifRejet(s == StatutDemande.ANNULEE
                        ? "Demande de correction annulée"
                        : "Demande de correction rejetée" + (demande.getMotifRejet() != null ? " : " + demande.getMotifRejet() : ""));
            }
            avenantRepository.save(a);
            auditService.log(AuditAction.UPDATE, "Avenant", String.valueOf(a.getId()),
                    Map.of("statut", a.getStatut().name(), "demandeCorrectionId", demande.getId()));
        }
    }
}
