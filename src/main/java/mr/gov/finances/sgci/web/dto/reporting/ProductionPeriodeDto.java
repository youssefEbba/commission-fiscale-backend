package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Granularite;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * La production par période, et son évolution par rapport à la période précédente.
 *
 * <p><b>Une variation nulle n'est pas zéro.</b> Quand la période antérieure est vide — parce qu'elle
 * précède le début des données — la variation vaut {@code null} et non {@code −100 %}, qui se
 * lirait « effondrement » alors qu'il signifie « pas de référence ». L'écran écrit « n/a ».
 *
 * <p><b>La clé de datation est publiée.</b> {@code CertificatCredit} n'a pas de date de création :
 * la masse accordée ne peut s'appuyer que sur la date de mise en place, avec repli sur la date
 * d'émission. C'est la même clé que le journal des crédits, délibérément, pour que les deux écrans
 * ne puissent pas se contredire — et {@link #cleDatation} le dit, pour que personne n'ait à le
 * deviner.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionPeriodeDto {

    private Granularite granularite;

    @Builder.Default
    private List<ProductionPointDto> points = new ArrayList<>();

    /** Ce que datent les montants, en clair. */
    private String cleDatation;

    // ── totaux de la période consultée ─────────────────────────────────────────────────────────

    private long totalDemandes;
    private long totalCertificats;
    private long totalUtilisations;
    private BigDecimal totalMasseAccordee;
    private BigDecimal totalConsommeMouvements;

    // ── la période précédente, de même durée ───────────────────────────────────────────────────

    private long precedentDemandes;
    private long precedentCertificats;
    private BigDecimal precedentMasseAccordee;

    /** {@code null} quand la période précédente est vide : pas de référence, pas de variation. */
    private Double variationDemandesPct;
    private Double variationCertificatsPct;
    private Double variationMassePct;

    private boolean filtersApplied;
}
