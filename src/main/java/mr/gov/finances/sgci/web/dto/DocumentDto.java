package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeDocument;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDto {

    private Long id;
    private TypeDocument type;
    private String nomFichier;
    private String chemin;
    private Instant dateUpload;
    private Long taille;
    private Integer version;
    private Boolean actif;
}
