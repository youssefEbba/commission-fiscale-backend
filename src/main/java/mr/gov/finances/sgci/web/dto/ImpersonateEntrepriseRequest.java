package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImpersonateEntrepriseRequest {
    @NotNull
    private Long entrepriseId;
}
