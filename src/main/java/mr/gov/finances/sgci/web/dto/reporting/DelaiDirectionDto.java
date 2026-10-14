package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Le délai moyen de visa d'une direction.
 *
 * <p><b>Deux réserves à énoncer, et non à laisser découvrir.</b> D'abord, le départ du chronomètre
 * n'est pas enregistré : ce délai court du dépôt du dossier au visa, et non de l'arrivée sur le
 * bureau de la direction à son visa. Les deux diffèrent beaucoup quand les visas sont séquentiels,
 * et c'est {@link #definitionDelai} qui doit le dire à l'écran.
 *
 * <p>Ensuite, les visas posés par un administrateur à la place de la direction sont comptés
 * <b>à part</b> : les fondre dans la moyenne ferait mesurer l'administrateur et non la direction.
 * Ce drapeau n'existant que sur les décisions de correction et de certificat,
 * {@link #nbVisasParAdmin} vaut zéro sur les deux autres circuits sans que cela prouve qu'il n'y en
 * a pas eu.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DelaiDirectionDto {

    private String role;

    /** Le circuit mesuré : correction, certificat, utilisation ou transfert. */
    private String circuit;

    private long nbVisas;

    /** Moyenne en jours, {@code null} si aucun visa n'est mesurable. */
    private Double delaiMoyenJours;

    private Long delaiMaxJours;

    /** Visas posés par un administrateur, exclus de la moyenne ci-dessus. */
    private long nbVisasParAdmin;

    private String definitionDelai;
}
