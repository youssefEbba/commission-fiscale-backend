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

    @NotNull(message = "L'entreprise est obligatoire")
    private Long entrepriseId;

    @NotNull(message = "La convention est obligatoire")
    private Long conventionId;

    private Long marcheId;

    @NotNull(message = "Le modèle fiscal est obligatoire")
    private ModeleFiscalDto modeleFiscal;

    @NotNull(message = "Le DQE est obligatoire")
    private DqeDto dqe;
}
