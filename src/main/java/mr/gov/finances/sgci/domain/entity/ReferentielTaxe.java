package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Référentiel des taxes douanières paramétrable par l'administrateur.
 * <p>
 * L'entreprise s'appuie sur ce référentiel pour composer les lignes de son
 * bulletin de liquidation (IM4). La valeur de référence est indicative ;
 * l'entreprise peut la modifier lors de la saisie.
 */
@Entity
@Table(name = "referentiel_taxe",
        uniqueConstraints = @UniqueConstraint(name = "uq_referentiel_taxe_code", columnNames = "code_taxe"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferentielTaxe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code court unique de la taxe (ex. "DD", "TVA", "RS", "PSC", "IMF"). */
    @Column(name = "code_taxe", length = 20, nullable = false)
    private String codeTaxe;

    /** Libellé complet de la taxe. */
    @Column(name = "denomination_taxe", length = 150, nullable = false)
    private String denominationTaxe;

    /**
     * Valeur de référence / taux indicatif (MRU ou %).
     * L'entreprise peut la modifier lors de la saisie du bulletin.
     * Null si la valeur est entièrement libre.
     */
    @Column(name = "valeur_taxe", precision = 19, scale = 4)
    private BigDecimal valeurTaxe;

    /** Ordre d'affichage dans le formulaire. */
    @Column(name = "ordre_affichage")
    @Builder.Default
    private Integer ordreAffichage = 0;

    /** Taxe désactivée : n'apparaît plus dans le formulaire mais les données historiques restent. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private Instant dateCreation = Instant.now();

    @Column(name = "date_modification")
    private Instant dateModification;
}
