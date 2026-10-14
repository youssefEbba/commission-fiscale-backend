package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Un dossier qui n'a pas bougé depuis un moment.
 *
 * <p>{@code joursDepuisDerniereDecision} se compte depuis la dernière décision enregistrée, avec
 * repli sur la date de dépôt ou d'émission du dossier. Ce n'est pas l'âge dans le statut courant,
 * que rien ne permet de calculer aujourd'hui : voir {@link BlocagesDto}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DossierImmobileDto {

    /** {@code DemandeCorrection}, {@code CertificatCredit}, {@code UtilisationCredit}, … */
    private String type;

    private Long id;

    /** La référence lisible du dossier, celle que l'agent reconnaît. */
    private String reference;

    private String statut;

    /** Dernière décision enregistrée, ou la date de repli utilisée faute de décision. */
    private Instant dateReference;

    /** Vrai si {@link #dateReference} est un repli et non une décision. */
    private boolean dateDeRepli;

    private long joursDepuisDerniereDecision;

    private String entrepriseRaisonSociale;
}
