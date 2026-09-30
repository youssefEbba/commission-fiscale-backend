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
     * Numéro du certificat d'utilisation ({@code CU-001/2026}), attribué une seule fois par le
     * Président à l'émission. Porté par la classe mère : les deux branches en ont un, la douane
     * comme la TVA intérieure. Héritage SINGLE_TABLE, donc même colonne physique qu'avant.
     */
    @Column(name = "numero_certificat_utilisation", length = 40)
    private String numeroCertificatUtilisation;

    @Column(name = "date_certificat_utilisation")
    private Instant dateCertificatUtilisation;

    /**
     * {@code TRUE} si ce dossier doit passer par l'émission présidentielle avant sa clôture.
     *
     * <p>Posé à {@code TRUE} par le calcul de la DGTCP (liquidation douanière, apurement TVA), donc
     * pour tout dossier instruit depuis l'introduction de l'étape. {@code FALSE} marque les dossiers
     * historiques, arrivés à leur statut final avant que l'application sache émettre un certificat :
     * leur réclamer un acte présidentiel rétroactif serait un faux, et les bloquer à la clôture une
     * régression. Ils sont marqués une seule fois par
     * {@code CertificatUtilisationGrandfatheringMigration}.
     *
     * <p>{@code null} n'a de sens que le temps qui sépare l'ajout de la colonne de ce rattrapage.
     */
    @Column(name = "emission_certificat_requise")
    private Boolean emissionCertificatRequise;

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
