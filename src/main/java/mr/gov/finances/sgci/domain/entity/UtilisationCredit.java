package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "utilisation_credit")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type_utilisation", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public abstract class UtilisationCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_utilisation", insertable = false, updatable = false)
    private TypeUtilisation type;

    /** Référence lisible standardisée (ex. DU-001-01/2025). */
    @Column(unique = true)
    private String reference;

    private Instant dateDemande;

    @Column(precision = 19, scale = 4)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 32)
    private StatutUtilisation statut = StatutUtilisation.DEMANDEE;

    private Instant dateLiquidation;

    /**
     * Libellé d'origine de la ligne dans le relevé d'archive, par exemple {@code UT 1 | 1/01/2026}.
     *
     * <p>Sert deux usages : renseigné, il marque une utilisation reprise d'une archive (par
     * opposition à une utilisation saisie dans l'application) ; sa valeur est la clé métier qui
     * évite de réimporter deux fois la même ligne lorsqu'un relevé est reversé complété.
     */
    @Column(name = "origine_archive_libelle", length = 160)
    private String origineArchiveLibelle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id", nullable = false)
    private CertificatCredit certificatCredit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;
}
