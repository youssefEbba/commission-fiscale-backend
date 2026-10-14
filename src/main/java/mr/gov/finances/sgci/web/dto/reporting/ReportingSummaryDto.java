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
public class ReportingSummaryDto {
    private ReportingDemandeStatsDto demandes;
    @Builder.Default
    private List<NamedCountDto> certificatsByStatut = new ArrayList<>();
    private long certificatsTotal;
    private Long certificatsEnValidationPresident;
    @Builder.Default
    private List<NamedCountDto> utilisationsByStatut = new ArrayList<>();
    @Builder.Default
    private List<NamedCountDto> utilisationsByType = new ArrayList<>();
    private long utilisationsTotal;
    @Builder.Default
    private List<NamedCountDto> conventionsByStatut = new ArrayList<>();
    @Builder.Default
    private List<NamedCountDto> referentielsByStatut = new ArrayList<>();
    @Builder.Default
    private List<NamedCountDto> marchesByStatut = new ArrayList<>();
    private long transfertsTotal;
    private long sousTraitancesTotal;
    private ReportingAuditStatsDto audit;
    /**
     * Totaux financiers des certificats du périmètre, jamais {@code null} hors périmètre fermé.
     *
     * <p>Le commentaire précédent annonçait « null si l'utilisateur n'a pas le droit des agrégats
     * financiers nationaux ». C'était faux — le calcul est inconditionnel — et la règle qu'il
     * décrivait n'a pas lieu d'exister : ces sommes portent sur les seuls certificats que
     * l'utilisateur a le droit de voir, donc une entreprise y lit les siens, et c'est normal.
     * Seul un périmètre fermé rend des zéros.
     */
    private CertificatFinancialTotalsDto certificatFinancials;
    /** Indique si les filtres demandés ont été appliqués (rôles nationaux) */
    private boolean filtersApplied;
}
