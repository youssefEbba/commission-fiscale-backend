package mr.gov.finances.sgci.web.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DossierGedDto {

    private Long id;
    private String reference;
    private Long entrepriseId;
    private Long certificatId;
    private Long demandeCorrectionId;
    private Instant dateCreation;
    private List<DossierEtapeGed> etapes;
}
