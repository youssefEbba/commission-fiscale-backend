package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntrepriseDto {

    private Long id;
    @NotBlank(message = "La raison sociale est obligatoire")
    private String raisonSociale;
    private String nif;
    private String adresse;
    private String situationFiscale;
}
