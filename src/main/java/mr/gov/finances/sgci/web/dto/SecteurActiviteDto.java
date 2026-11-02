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
public class SecteurActiviteDto {
    private Long id;
    @NotBlank(message = "Le code est obligatoire")
    private String code;
    @NotBlank(message = "Le libellé est obligatoire")
    private String libelle;
    private String libelleAr;
    private Boolean actif;
}
