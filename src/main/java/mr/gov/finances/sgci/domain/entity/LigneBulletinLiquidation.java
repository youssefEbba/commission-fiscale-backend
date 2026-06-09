package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;
import mr.gov.finances.sgci.domain.enums.TypeLigneTaxe;

import java.math.BigDecimal;

/**
 * Une ligne du bulletin de liquidation douanière.
 * <p>
 * L'entreprise saisit {@code codeTaxe}, {@code denominationTaxe}, {@code typeLigne}, {@code valeurTaxe}
 * et propose {@code affectationEntreprise} (AU_CI = pris en charge par le crédit, A_PAYER = comptant).
 * Le DGD valide ou modifie via {@code affectation} (décision finale) lors du visa.
 */
@Entity
@Table(name = "ligne_bulletin_liquidation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneBulletinLiquidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code court de la taxe : "DD", "TVA", "RS", "PSC", "IMF", "PC", "TSI", etc. */
    @Column(length = 20)
    private String codeTaxe;

    /** Libellé complet tel qu'il apparaît sur le bulletin (ex. "Droit de Douane"). */
    @Column(length = 120)
    private String denominationTaxe;

    /** Section du bulletin : GLOBALE (en-tête) ou ARTICLE (par ligne de marchandise). */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TypeLigneTaxe typeLigne;

    /** Valeur saisie par l'entreprise (MRU). */
    @Column(precision = 19, scale = 4)
    private BigDecimal valeurTaxe;

    /**
     * Proposition entreprise : AU_CI (cordon / crédit d'impôt) ou A_PAYER (à payer comptant).
     * Renseignée à la création ou mise à jour de la demande (brouillon ou soumission).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "affectation_entreprise", length = 10)
    private AffectationTaxe affectationEntreprise;

    /**
     * Décision finale DGD : valide ou modifie la proposition entreprise.
     * Null tant que le visa DGD n'est pas enregistré.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AffectationTaxe affectation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisation_douaniere_id", nullable = false)
    private UtilisationDouaniere utilisationDouaniere;
}
