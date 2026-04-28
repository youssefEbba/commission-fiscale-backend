package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutDemande;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarcheDemandeCorrectionStatusDto {

    private Long marcheId;
    private boolean hasActiveDemandeCorrection;
    private Long demandeCorrectionId;
    private StatutDemande demandeCorrectionStatut;
}
