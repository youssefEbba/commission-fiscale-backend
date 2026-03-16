package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiquiderUtilisationDouaneRequest {

    @NotNull
    @PositiveOrZero
    private BigDecimal montantDroits;

    @NotNull
    @PositiveOrZero
    private BigDecimal montantTVA;
}
