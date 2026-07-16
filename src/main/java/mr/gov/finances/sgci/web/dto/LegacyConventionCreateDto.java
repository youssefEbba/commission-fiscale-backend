package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
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
public class LegacyConventionCreateDto {

    @NotBlank(message = "La référence de convention est obligatoire")
    private String reference;

    @NotBlank(message = "L'intitulé de convention est obligatoire")
    private String intitule;

    private Long bailleurId;
    private LocalDate dateSignature;
    private LocalDate dateFin;
    private BigDecimal montantDevise;
    private BigDecimal montantMru;
    private String deviseOrigine;
    private BigDecimal tauxChange;
    private String projectReference;
}
