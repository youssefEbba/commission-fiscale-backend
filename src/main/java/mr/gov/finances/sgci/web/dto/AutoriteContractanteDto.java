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
public class AutoriteContractanteDto {

    private Long id;
    @NotBlank(message = "Le nom est obligatoire")
    private String nom;
    private String code;
    private String contact;
}
