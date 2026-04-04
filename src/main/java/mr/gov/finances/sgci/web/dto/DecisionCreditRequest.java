package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.TypeDocument;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecisionCreditRequest {

    @NotNull
    private DecisionCorrectionType decision;

    private String motifRejet;

    private List<TypeDocument> documentsDemandes;
}
