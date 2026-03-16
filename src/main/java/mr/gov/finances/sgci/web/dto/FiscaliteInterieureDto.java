package mr.gov.finances.sgci.web.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FiscaliteInterieureDto {
    private Long id;
    private Double montantHT;
    private Double tauxTVA;
    private Double autresTaxes;
    private Double tvaCollectee;
    private Double tvaDeductible;
    private Double tvaNette;
    private Double creditInterieur;
}
