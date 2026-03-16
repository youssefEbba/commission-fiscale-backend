package mr.gov.finances.sgci.web.dto;

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
public class DocumentRequirementDto {

    private Long id;
    private ProcessusDocument processus;
    private TypeDocument typeDocument;
    private Boolean obligatoire;
    private Set<TypeFichierAutorise> typesAutorises;
    private String description;
    private Integer ordreAffichage;
}
