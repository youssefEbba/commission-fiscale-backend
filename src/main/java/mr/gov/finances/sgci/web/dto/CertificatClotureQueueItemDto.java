package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Certificat visible dans la file de clôture DGTCP (tous les OUVERT / MODIFIE non finalisés),
 * avec indicateur d'éligibilité et motifs de blocage éventuels.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatClotureQueueItemDto {

    private Long certificatCreditId;
    private String numero;
    private StatutCertificat statut;
    private Instant dateEmission;
    private Instant dateValidite;
    private String entrepriseRaisonSociale;

    private BigDecimal soldeCordon;
    private BigDecimal soldeTVA;
    private BigDecimal tvaImportationDouane;
    private BigDecimal stockTvaDeductibleRestant;

    /** true si une proposition de clôture est en cours (validation Président). */
    private boolean propositionEnCours;
    private Long clotureCreditId;

    /** true si le DGTCP peut proposer une clôture maintenant (règles métier). */
    private boolean eligiblePourCloture;

    /** Vide si {@link #eligiblePourCloture} ; sinon explications en français. */
    private List<String> motifsNonEligibilite;
}
