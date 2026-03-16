package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTransfertCreditRequest {

    @NotNull
    private Long certificatCreditId;

    @NotNull
    private BigDecimal montant;

    private Boolean operationsDouaneCloturees;
}
