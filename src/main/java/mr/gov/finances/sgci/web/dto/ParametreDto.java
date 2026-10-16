package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Un réglage de l'application, tel que l'écran de paramétrage le montre. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParametreDto {

    private String cle;

    private String valeur;

    private String description;

    private Instant dateModification;

    /**
     * Vrai si ce réglage est lisible sans être connecté.
     *
     * <p>Affiché pour que l'agent sache ce qu'il rend visible en le modifiant : les coordonnées de
     * contact apparaissent sur la page de connexion, donc avant toute authentification.
     */
    private boolean publique;
}
