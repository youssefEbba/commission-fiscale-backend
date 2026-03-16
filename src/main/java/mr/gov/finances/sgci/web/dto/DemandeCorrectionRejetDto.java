package mr.gov.finances.sgci.web.dto;

import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeCorrectionRejetDto {

    private Long id;
    private String motifRejet;
    private Instant dateRejet;
    private Long utilisateurId;
    private String utilisateurNom;
}
