package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferentielTaxeDto {

    private Long id;
    private String codeTaxe;
    private String denominationTaxe;
    private BigDecimal valeurTaxe;
    private Integer ordreAffichage;
    private Boolean active;
    private Instant dateCreation;
    private Instant dateModification;
}
