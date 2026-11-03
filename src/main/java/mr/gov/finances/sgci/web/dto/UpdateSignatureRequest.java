package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Modification de métadonnées uniquement (pas du fichier — voir {@code /remplacer} pour le contenu). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSignatureRequest {
    @Size(max = 120)
    private String nomAffiche;
    private Boolean active;
}
