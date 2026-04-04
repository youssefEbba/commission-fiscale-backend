package mr.gov.finances.sgci.web.dto;

import lombok.*;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeDocument;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionCreditDto {

    private Long id;
    private Role role;
    private DecisionCorrectionType decision;
    private String motifRejet;
    private Set<TypeDocument> documentsDemandes;
    private Instant dateDecision;
    private RejetTempStatus rejetTempStatus;
    private Instant rejetTempResolvedAt;
    private Long utilisateurId;
    private String utilisateurNom;

    private List<RejetTempResponseDto> rejetTempResponses;
}
