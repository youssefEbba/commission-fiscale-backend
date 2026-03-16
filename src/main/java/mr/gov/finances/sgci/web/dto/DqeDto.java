package mr.gov.finances.sgci.web.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DqeDto {
    private Long id;
    private String numeroAAOI;
    private String projet;
    private String lot;
    private BigDecimal tauxTVA;
    private BigDecimal totalHT;
    private BigDecimal montantTVA;
    private BigDecimal totalTTC;
    @Builder.Default
    private List<DqeLigneDto> lignes = new ArrayList<>();
}
