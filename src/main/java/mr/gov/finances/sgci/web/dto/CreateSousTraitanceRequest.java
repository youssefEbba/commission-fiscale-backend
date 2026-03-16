package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSousTraitanceRequest {

    @NotNull
    private Long certificatCreditId;

    @NotNull
    private Long sousTraitantEntrepriseId;

    private Boolean contratEnregistre;

    private BigDecimal volumes;

    private BigDecimal quantites;
}
