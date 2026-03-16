package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertDocumentRequirementRequest {

    @NotNull
    private ProcessusDocument processus;

    @NotNull
    private TypeDocument typeDocument;

    @NotNull
    private Boolean obligatoire;

    private Set<TypeFichierAutorise> typesAutorises;

    private String description;

    private Integer ordreAffichage;
}
