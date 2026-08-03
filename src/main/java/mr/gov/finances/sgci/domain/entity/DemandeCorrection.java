package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutDemande;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "demande_correction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String numero;

    /** Référence lisible standardisée (ex. DC-001-01/2025). Distincte de {@link #numero} (interne/unicité). */
    @Column(unique = true)
    private String reference;

    private Instant dateDepot;

    /**
     * Intitulé libre du marché / convention saisi à la demande de correction.
     * Le marché réel n'est créé qu'à la mise en place ; ce champ conserve la désignation en attendant.
     */
    @Column(length = 500)
    private String intituleMarche;

    /**
     * Montant du crédit intérieur (TVA intérieure) demandé. Défaut 0.
     * Si 0 → la DGI est exclue du workflow de visa (voir VisaRequirementResolver).
     */
    @Column(precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal creditInterieur = BigDecimal.ZERO;

    /**
     * Montant du crédit extérieur (douanier / cordon) demandé. Défaut 0.
     * Si 0 → la DGD est exclue du workflow de visa (voir VisaRequirementResolver).
     */
    @Column(precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal creditExterieur = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutDemande statut = StatutDemande.RECUE;

    @Builder.Default
    private boolean validationDgd = false;

    private Long validationDgdUserId;

    private Instant validationDgdDate;

    @Builder.Default
    private boolean validationDgtcp = false;

    private Long validationDgtcpUserId;

    private Instant validationDgtcpDate;

    @Builder.Default
    private boolean validationDgi = false;

    private Long validationDgiUserId;

    private Instant validationDgiDate;

    @Builder.Default
    private boolean validationDgb = false;

    private Long validationDgbUserId;

    private Instant validationDgbDate;

    private Instant dateCreation;
    private Instant dateModification;

    @Column(length = 1000)
    private String motifRejet;

    /** Id du marché au moment du détachement (demande annulée) — traçabilité. */
    private Long marcheIdTrace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_contractante_id", nullable = false)
    private AutoriteContractante autoriteContractante;

    /**
     * Titulaire effectif du dossier (toujours renseigné).
     * Si {@link #groupement} est présent, ce champ pointe automatiquement vers le chef de file.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

    /**
     * Groupement porteur de la demande (optionnel).
     * Lorsqu'il est renseigné, {@link #entreprise} est forcé au chef de file du groupement
     * (le NIF affiché du dossier est celui du chef de file).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupement_id")
    private Groupement groupement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convention_id", nullable = false)
    private Convention convention;

    @OneToOne(mappedBy = "demandeCorrection", cascade = CascadeType.ALL, orphanRemoval = true)
    private ModeleFiscal modeleFiscal;

    @OneToOne(mappedBy = "demandeCorrection", cascade = CascadeType.ALL, orphanRemoval = true)
    private Dqe dqe;

    @OneToMany(mappedBy = "demandeCorrection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Document> documents = new ArrayList<>();

    @OneToOne(mappedBy = "demandeCorrection", cascade = CascadeType.ALL, orphanRemoval = true)
    private FeuilleEvaluation feuilleEvaluation;

    /** Inverse côté {@link Marche#demandeCorrection} — pas d'orphanRemoval ici sinon annulation = suppression du marché. */
    @OneToOne(mappedBy = "demandeCorrection")
    private Marche marche;

    @OneToMany(mappedBy = "demandeCorrection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DemandeCorrectionRejet> rejets = new ArrayList<>();

    @OneToMany(mappedBy = "demandeCorrection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DecisionCorrection> decisions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        dateCreation = Instant.now();
        dateModification = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = Instant.now();
    }

    
}
