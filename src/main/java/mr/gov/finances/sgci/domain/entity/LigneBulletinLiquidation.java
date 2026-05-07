package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;
import mr.gov.finances.sgci.domain.enums.TypeLigneTaxe;

import java.math.BigDecimal;

/**
 * Une ligne du bulletin de liquidation douanière.
 * <p>
 * L'entreprise saisit {@code codeTaxe}, {@code denominationTaxe}, {@code typeLigne} et {@code valeurTaxe}.
 * Le DGD renseigne ensuite {@code affectation} (AU_CI ou A_PAYER) lors de la liquidation.
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
     * Décision DGD : AU_CI (pris en charge par le crédit d'impôt) ou A_PAYER (paiement comptant).
     * Null tant que la liquidation n'est pas effectuée.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AffectationTaxe affectation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisation_douaniere_id", nullable = false)
    private UtilisationDouaniere utilisationDouaniere;
}
