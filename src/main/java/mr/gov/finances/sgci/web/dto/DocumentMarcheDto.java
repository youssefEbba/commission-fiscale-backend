package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeDocumentMarche;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMarcheDto {

    private Long id;
    private TypeDocumentMarche type;
    private String nomFichier;
    private String chemin;
    private Instant dateUpload;
    private Long taille;
}
