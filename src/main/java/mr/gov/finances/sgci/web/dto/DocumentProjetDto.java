package mr.gov.finances.sgci.web.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeDocumentProjet;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentProjetDto {
    private Long id;
    private TypeDocumentProjet type;
    private String nomFichier;
    private String chemin;
    private Instant dateUpload;
    private Long taille;
}
