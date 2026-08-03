package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Journal daté des crédits d'impôt mis en place (statut OUVERT / MODIFIE / CLOTURE) sur une période,
 * avec les agrégats financiers correspondants.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatCreditJournalDto {

    private Instant from;
    private Instant to;

    /** Page des certificats mis en place sur la période. */
    private PageResponse<CertificatCreditDto> certificats;

    /** Nombre total de crédits mis en place sur la période (tous éléments, pas seulement la page). */
    private long nombreCredits;
    private BigDecimal totalMontantCordon;
    private BigDecimal totalMontantTVAInterieure;
    private BigDecimal totalSoldeCordon;
    private BigDecimal totalSoldeTVA;
}
