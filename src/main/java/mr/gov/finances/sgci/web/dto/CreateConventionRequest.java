package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateConventionRequest {

    @NotBlank(message = "La référence est obligatoire")
    private String reference;

    @NotBlank(message = "L'intitulé est obligatoire")
    private String intitule;

    private String bailleur;
    private String bailleurDetails;
    private LocalDate dateSignature;
    private LocalDate dateFin;
    private BigDecimal montantDevise;
    private BigDecimal montantMru;
    private String deviseOrigine;
    private BigDecimal tauxChange;

    /** Référence projet (optionnel). */
    private String projectReference;

    private Long autoriteContractanteId;
}
