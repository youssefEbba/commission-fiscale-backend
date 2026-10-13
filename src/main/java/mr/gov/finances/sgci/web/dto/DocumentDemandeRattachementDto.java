package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Un justificatif joint à une demande de rattachement. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDemandeRattachementDto {

    private Long id;
    private String codeDocument;
    private String nomFichier;
    private Long taille;
    private Instant dateUpload;
}
