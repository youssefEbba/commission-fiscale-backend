package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "certificat_credit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String numero;

    private Instant dateEmission;
    private Instant dateValidite;

    /**
     * Enveloppe « crédit cordon / extérieur » (récap. fiscal : ligne e = b + d, imputations douane).
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal montantCordon;

    /**
     * Enveloppe « crédit TVA intérieure » (récap. fiscal : ligne h = g − d, décomptes / achats locaux).
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal montantTVAInterieure;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeCordon;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeTVA;

    /** Récap. : (a) valeur en douane des fournitures importées. */
    @Column(precision = 19, scale = 4)
    private BigDecimal valeurDouaneFournitures;

    /** Récap. : (b) droits et taxes douaniers (hors ventilation TVA import). */
    @Column(precision = 19, scale = 4)
    private BigDecimal droitsEtTaxesDouaneHorsTva;

    /**
     * Récap. : (d) TVA à l’import — montant **accordé initialement** (figé à la saisie DGTCP / création).
     * Sert aux contrôles de cohérence avec (b), (g) et aux champs dérivés en lecture.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal tvaImportationDouaneAccordee;

    /**
     * Restant de la ligne (d) après chaque liquidation douanière : la TVA d’importation réelle imputée
     * est retranchée de ce solde lors de la liquidation douanière (endpoint dédié côté service utilisations).
     * À l’ouverture / après saisie des montants, égal à {@link #tvaImportationDouaneAccordee}.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal tvaImportationDouane;

    /** Récap. : (f) montant du marché / offre HT (travaux). */
    @Column(precision = 19, scale = 4)
    private BigDecimal montantMarcheHt;

    /** Récap. : (g) TVA collectée sur travaux (ex. 16 % × f). */
    @Column(precision = 19, scale = 4)
    private BigDecimal tvaCollecteeTravaux;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutCertificat statut = StatutCertificat.EN_CONTROLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lettre_correction_id")
    private LettreCorrection lettreCorrection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id")
    private DemandeCorrection demandeCorrection;

    @OneToMany(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Item> items = new ArrayList<>();

    @OneToMany(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UtilisationCredit> utilisations = new ArrayList<>();

    @OneToMany(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Avenant> avenants = new ArrayList<>();

    @OneToOne(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    private TransfertCredit transfertCredit;

    @OneToOne(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    private SousTraitance sousTraitance;

    @OneToOne(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    private ClotureCredit clotureCredit;
}
