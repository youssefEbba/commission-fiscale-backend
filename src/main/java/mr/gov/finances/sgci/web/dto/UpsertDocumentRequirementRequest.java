package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertDocumentRequirementRequest {

    @NotNull
    private ProcessusDocument processus;

    @NotBlank
    @Size(max = 64)
    private String codeDocument;

    /** Libellé pour création inline du type dans le référentiel si absent. */
    @Size(max = 500)
    private String libelle;

    @NotNull
    private Boolean obligatoire;

    private Set<TypeFichierAutorise> typesAutorises;

    private String description;

    private Integer ordreAffichage;
}
