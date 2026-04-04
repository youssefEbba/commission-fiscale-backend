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
    @Builder.Default
    private List<NamedCountDto> byStatut = new ArrayList<>();
    private long total;
    private Double tauxAdoptionPct;
    private Double tauxRejetPct;
}
