package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Un rejet temporaire encore ouvert : la Commission attend une pièce de l'assujetti.
 *
 * <p>Le chiffre le plus solide du bloc des blocages. L'état est explicite en base et sa valeur par
 * défaut est {@code RESOLU}, donc « ouvert » n'est jamais déduit d'une absence de donnée. L'agent,
 * lui, est atteint par une vraie clé étrangère — contrairement aux visas portés par la demande, qui
 * ne gardent qu'un identifiant nu, sans jointure possible.
 *
 * <p>Aucune fenêtre temporelle ne filtre cette liste : un rejet ouvert depuis soixante-seize jours
 * compte, même si sa décision est antérieure à la période consultée. C'est un état courant, pas un
 * événement de la période.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejetTempOuvertDto {

    /** Le dossier concerné : {@code DemandeCorrection}, {@code CertificatCredit}, … */
    private String type;

    private Long dossierId;

    private String reference;

    /** La direction qui a posé le rejet. */
    private String role;

    private String agentUsername;

    private Instant dateDecision;

    private long joursOuvert;

    private String motifRejet;
}
