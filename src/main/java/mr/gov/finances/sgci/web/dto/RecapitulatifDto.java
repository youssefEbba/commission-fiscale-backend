package mr.gov.finances.sgci.web.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecapitulatifDto {
    private Long id;
    private Double creditExterieur;
    private Double creditInterieur;
    private Double creditTotal;
}
