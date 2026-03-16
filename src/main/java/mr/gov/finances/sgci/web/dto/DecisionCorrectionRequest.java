package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecisionCorrectionRequest {

    @NotNull
    private DecisionCorrectionType decision;
    private String motifRejet;
}
