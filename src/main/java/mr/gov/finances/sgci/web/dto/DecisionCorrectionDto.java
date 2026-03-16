package mr.gov.finances.sgci.web.dto;

import lombok.*;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.Role;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionCorrectionDto {

    private Long id;
    private Role role;
    private DecisionCorrectionType decision;
    private String motifRejet;
    private Instant dateDecision;
    private Long utilisateurId;
    private String utilisateurNom;
}
