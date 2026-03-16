package mr.gov.finances.sgci.web.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeDocumentConvention;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentConventionDto {
    private Long id;
    private TypeDocumentConvention type;
    private String nomFichier;
    private String chemin;
    private Instant dateUpload;
    private Long taille;
}
