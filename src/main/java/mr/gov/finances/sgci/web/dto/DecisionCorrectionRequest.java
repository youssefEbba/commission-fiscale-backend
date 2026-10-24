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
public class DecisionCorrectionRequest {

    @NotNull
    private DecisionCorrectionType decision;
    private String motifRejet;

    private List<String> documentsDemandes;

    /**
     * Crédits corrigés. Obligatoires avec le visa de la direction qui dépose l'offre corrigée — la
     * DGD s'il y a un crédit extérieur, sinon la DGI — et refusés partout ailleurs. L'écran les
     * préremplit avec les montants actuels de la demande ; la direction les garde ou les change.
     */
    private java.math.BigDecimal creditExterieur;
    private java.math.BigDecimal creditInterieur;
}
