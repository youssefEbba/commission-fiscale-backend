package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReferentielTaxeRequest {

    @NotBlank(message = "Le code taxe est obligatoire")
    @Size(max = 20, message = "Le code taxe ne peut pas dépasser 20 caractères")
    private String codeTaxe;

    @NotBlank(message = "La dénomination est obligatoire")
    @Size(max = 150)
    private String denominationTaxe;

    /** Valeur indicative (optionnelle). */
    private BigDecimal valeurTaxe;

    /** Ordre d'affichage dans le formulaire (défaut : 0). */
    private Integer ordreAffichage;

    /** true = visible dans les formulaires (défaut : true). */
    private Boolean active;
}
