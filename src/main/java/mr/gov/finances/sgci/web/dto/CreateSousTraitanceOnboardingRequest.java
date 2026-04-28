package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSousTraitanceOnboardingRequest {

    @NotNull(message = "Le certificatCreditId est obligatoire")
    private Long certificatCreditId;

    @NotBlank(message = "La raison sociale de l'entreprise sous-traitante est obligatoire")
    private String sousTraitantEntrepriseRaisonSociale;

    private String sousTraitantEntrepriseNomCommercial;

    private String sousTraitantEntrepriseActivite;

    private String sousTraitantEntrepriseAutre;

    private String sousTraitantEntrepriseNif;

    private String sousTraitantEntrepriseAdresse;

    private String sousTraitantEntrepriseSituationFiscale;

    private Boolean contratEnregistre;

    private BigDecimal volumes;

    private BigDecimal quantites;
}
