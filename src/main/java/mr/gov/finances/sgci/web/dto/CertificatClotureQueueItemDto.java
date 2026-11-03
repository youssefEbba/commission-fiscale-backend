package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Certificat visible dans la file de clôture DGTCP (tous les OUVERT / MODIFIE non finalisés),
 * avec indicateur d'éligibilité et motifs de blocage éventuels.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatClotureQueueItemDto {

    private Long certificatCreditId;
    private String numero;
    /** Référence lisible du crédit (CR-…), celle de « Mes crédits ». */
    private String reference;
    private StatutCertificat statut;
    private Instant dateEmission;
    private Instant dateValidite;
    private String entrepriseRaisonSociale;

    private BigDecimal soldeCordon;
    private BigDecimal soldeTVA;
    private BigDecimal tvaImportationDouane;
    private BigDecimal stockTvaDeductibleRestant;

    /** true si une proposition de clôture est en cours (validation Président). */
    private boolean propositionEnCours;
    private Long clotureCreditId;

    /**
     * true si le DGTCP peut proposer une clôture maintenant : aucune opération en cours. Le motif
     * choisi peut ensuite exiger davantage (solde à zéro, validité dépassée).
     */
    private boolean eligiblePourCloture;

    /** true si plus rien ne retient le crédit : soldes épuisés ou validité dépassée. */
    private boolean detecteAutomatiquement;

    /** true si tous les soldes (cordon, TVA intérieure, TVA importation, stock déductible) sont nuls. */
    private boolean soldesEpuises;

    /** true si la date de validité du crédit est dépassée. */
    private boolean validiteDepassee;

    /** Les soldes encore disponibles, en français ; vide si {@link #soldesEpuises}. */
    private List<String> soldesRestants;

    /**
     * Vrai si la demande de correction d'origine déclarait des équipements sous régime (plaques TT).
     *
     * <p>C'est à la clôture que l'information compte : fermer un crédit dont des équipements restent
     * sous régime est précisément ce que la Commission veut être en mesure de voir avant de signer.
     */
    private Boolean equipementsPresents;

    /**
     * La demande de correction d'origine, pour que l'écran atteigne la liste des équipements.
     *
     * <p>Les pièces sont rattachées à la demande, pas au certificat : sans cet identifiant, l'écran
     * de clôture annoncerait des équipements sans permettre de les regarder, ou devrait rappeler le
     * certificat pour les retrouver.
     */
    private Long demandeCorrectionId;

    /** Vide si {@link #eligiblePourCloture} ; sinon explications en français. */
    private List<String> motifsNonEligibilite;
}
