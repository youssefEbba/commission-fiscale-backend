package mr.gov.finances.sgci.web.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DqeLigneDto {
    private Long id;
    private String designation;
    private String unite;
    private BigDecimal quantite;
    private BigDecimal prixUnitaireHT;
    private BigDecimal montantHT;
}
