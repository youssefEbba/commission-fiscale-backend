package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportingDemandeStatsDto {
    /** Demandes de correction déposées, par statut (hors brouillons et hors reprise d'archive). */
    @Builder.Default
    private List<NamedCountDto> byStatut = new ArrayList<>();
    /**
     * Toutes les demandes déposées dans la période, tous processus confondus, hors brouillons et hors
     * reprise d'archive : la somme de {@link #parProcessus}.
     */
    private long total;
    /**
     * Le détail de {@link #total} : CORRECTION, MISE_EN_PLACE, UTILISATION_DOUANE,
     * UTILISATION_TVA_INTERIEURE, TRANSFERT, AVENANT, SOUS_TRAITANCE (toujours présents, à 0 au besoin).
     */
    @Builder.Default
    private List<NamedCountDto> parProcessus = new ArrayList<>();
    private Double tauxAdoptionPct;
    private Double tauxRejetPct;
}
