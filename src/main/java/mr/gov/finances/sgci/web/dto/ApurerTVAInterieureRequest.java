package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApurerTVAInterieureRequest {

    /**
     * Montant de TVA déductible à consommer depuis le stock FIFO.
     * Si null, le système consomme automatiquement tout le stock disponible
     * (FIFO, ordre chronologique des importations).
     */
    @PositiveOrZero
    private BigDecimal tvaDeductibleUtilisee;
}
