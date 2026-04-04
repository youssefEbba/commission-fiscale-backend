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
public class ReportingAuditStatsDto {
    @Builder.Default
    private List<NamedCountDto> byAction = new ArrayList<>();
    @Builder.Default
    private List<NamedCountDto> topEntityTypes = new ArrayList<>();
    private long totalActions;
}
