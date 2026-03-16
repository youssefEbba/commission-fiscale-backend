package mr.gov.finances.sgci.web.dto;

import lombok.*;
import mr.gov.finances.sgci.domain.enums.TypeDocument;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSousTraitanceDto {

    private Long id;
    private TypeDocument type;
    private String nomFichier;
    private String chemin;
    private Instant dateUpload;
    private Long taille;
    private Integer version;
    private Boolean actif;
}
