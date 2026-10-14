package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Une période de la série de production.
 *
 * <p>Les périodes sans aucune donnée sont présentes, à zéro : une série qui saute les mois vides
 * dessine une courbe continue là où il y a eu un arrêt, et le creux disparaît avec l'information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionPointDto {

    /** {@code 2026-07} en mensuel, {@code 2026-T3} en trimestriel. */
    private String periode;

    private long nbDemandes;

    private long nbCertificats;

    private long nbUtilisations;

    /** Crédit accordé sur la période, daté comme le dit {@code cleDatation}. */
    private BigDecimal masseAccordee;

    /**
     * Consommé de la période, mesuré sur les <b>mouvements</b>.
     *
     * <p>Nommé différemment du {@code consommeSoldes} de l'engagement, et ce n'est pas un détail :
     * un solde est un niveau, il n'a pas de mois. Le bloc de production ne peut donc mesurer que
     * les mouvements, et les deux chiffres ne se réconcilient pas sur les dossiers repris de
     * l'ancienne application, dont les soldes ont été importés sans mouvements. Deux chiffres
     * nommés pareil et différents de trente pour cent détruisent la confiance plus sûrement qu'un
     * chiffre manquant.
     */
    private BigDecimal consommeMouvementsPeriode;
}
