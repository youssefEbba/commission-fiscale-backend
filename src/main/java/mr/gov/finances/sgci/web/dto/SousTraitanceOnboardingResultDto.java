package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SousTraitanceOnboardingResultDto {

    private Long sousTraitantEntrepriseId;

    private SousTraitanceDto sousTraitance;
}
