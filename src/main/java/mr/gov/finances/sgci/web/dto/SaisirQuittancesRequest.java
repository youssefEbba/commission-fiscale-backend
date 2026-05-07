package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Liste des quittances Trésor saisies par le DGTCP.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaisirQuittancesRequest {

    @NotEmpty(message = "Au moins une quittance est obligatoire")
    private List<QuittanceItem> quittances;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuittanceItem {
        private String numeroQuittance;
        private Instant dateQuittance;
        private BigDecimal montant;
        private String referencePaiement;
    }
}
