package mr.gov.finances.sgci.web.dto.archive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Réponse du service « Vérifier NIF » de la DGI pour un NIF. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationNifDto {
    private String nif;
    private boolean trouve;
    private String raisonSociale;
    private String nomCommercial;
    /** Troisième champ de la réponse DGI, rendu tel quel (signification non publiée). */
    private String etat;
    /** Date de mise à jour annoncée par la DGI. */
    private String dateMiseAJour;
}
