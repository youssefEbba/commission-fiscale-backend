package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SousTraitantUtilisateurDto {

    private Long id;
    private String username;
    private String nomComplet;
    private String email;
    private Boolean actif;

    private Long entrepriseId;
    private String entrepriseRaisonSociale;
    private String entrepriseNif;
}
