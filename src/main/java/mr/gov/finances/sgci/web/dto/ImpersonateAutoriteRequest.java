package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImpersonateAutoriteRequest {
    @NotNull
    private Long autoriteContractanteId;
}
