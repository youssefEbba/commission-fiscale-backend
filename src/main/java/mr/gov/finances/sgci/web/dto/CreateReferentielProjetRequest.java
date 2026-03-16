package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReferentielProjetRequest {
    private Long autoriteContractanteId;

    @NotNull(message = "La convention est obligatoire")
    private Long conventionId;

    private String nomProjet;
    private String administrateurProjet;
    private String referenceBciSecteur;
}
