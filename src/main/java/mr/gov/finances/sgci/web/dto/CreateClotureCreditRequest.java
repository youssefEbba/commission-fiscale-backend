package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.MotifCloture;
import mr.gov.finances.sgci.domain.enums.TypeOperationCloture;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateClotureCreditRequest {

    @NotNull(message = "certificatCreditId est obligatoire")
    private Long certificatCreditId;

    @NotNull(message = "motif est obligatoire")
    private MotifCloture motif;

    @NotNull(message = "typeOperation est obligatoire")
    private TypeOperationCloture typeOperation;
}
