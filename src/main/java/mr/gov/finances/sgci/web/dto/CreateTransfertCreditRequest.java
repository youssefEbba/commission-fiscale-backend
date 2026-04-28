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

    /**
     * Indicatif à la demande. À la validation, le montant exécuté = tout le restant (d) sur le certificat.
     */
    private BigDecimal montant;

    private Boolean operationsDouaneCloturees;
}
