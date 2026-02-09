package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDemandeCorrectionRequest {

    @NotNull(message = "L'autorité contractante est obligatoire")
    private Long autoriteContractanteId;
}
