package mr.gov.finances.sgci.web.dto.maincourante;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Un dossier qui attend quelque chose de l'utilisateur connecté.
 *
 * <p>{@code action} dit ce qui est attendu — « Poser le visa DGD », « Approuver la proposition de
 * clôture » — plutôt que de laisser l'agent déduire son geste d'un nom de statut. C'est la
 * différence entre une liste de dossiers et une liste de travail.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneMainCouranteDto {

    /** CORRECTION, MISE_EN_PLACE, TRANSFERT, CLOTURE ou REJET_TEMPORAIRE. */
    private String processus;

    /** Le type d'objet : {@code DemandeCorrection}, {@code CertificatCredit}, … */
    private String type;

    private Long id;

    /** La référence lisible, celle que l'agent reconnaît. */
    private String reference;

    private String statut;

    private String entrepriseRaisonSociale;

    /** Ce que l'utilisateur doit faire. */
    private String action;

    /**
     * Depuis quand le dossier attend.
     *
     * <p>C'est la date de la dernière étape connue — dépôt, émission, proposition, décision de
     * rejet — et non l'entrée dans le statut courant, qu'aucune donnée ne permet encore de
     * calculer. Voir {@code MainCouranteDto.definitionAttente}.
     */
    private Instant dateReference;

    private long joursDAttente;
}
