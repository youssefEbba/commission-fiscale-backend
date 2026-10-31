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
     * Référence de l'appel d'offres dont le marché est issu, demandée par l'équipe en octobre 2026.
     * Facultative et nullable : les demandes antérieures n'en ont pas, et {@code ddl-auto} ajoute la
     * colonne sans valeur par défaut.
     */
    @Column(name = "reference_appel_offre", length = 100)
    private String referenceAppelOffre;

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

    /**
     * Crédits tels que l'autorité contractante les a déclarés, avant la correction.
     *
     * <p>La direction qui dépose l'offre corrigée — la DGD s'il y a un crédit extérieur, sinon la
     * DGI — saisit les montants corrigés avec son visa, et ceux-ci remplacent {@link #creditInterieur}
     * et {@link #creditExterieur}, qui restent les montants de référence pour la suite du circuit.
     * Ces deux champs conservent la déclaration d'origine, pour qu'on puisse toujours lire ce que
     * l'autorité avait demandé à côté de ce que la Commission a retenu.
     *
     * <p>{@code null} tant qu'aucune correction n'a été saisie.
     */
    @Column(name = "credit_interieur_declare", precision = 19, scale = 4)
    private BigDecimal creditInterieurDeclare;

    @Column(name = "credit_exterieur_declare", precision = 19, scale = 4)
    private BigDecimal creditExterieurDeclare;

    /**
     * Équipements ou véhicules sous régime (plaques TT) acquis pendant le marché.
     *
     * <p>Nullable à dessein, là où les drapeaux de visa voisins sont des primitifs : une colonne
     * {@code NOT NULL} ajoutée sur une table peuplée aurait été remplie de zéros, ce qui tombe juste
     * ici mais par hasard. {@code null} dit la vérité sur les dossiers antérieurs — la question ne
     * leur a pas été posée — et se lit partout comme « non », sans migration de rattrapage.
     */
    @Column(name = "equipements_presents")
    private Boolean equipementsPresents;

    /**
     * Le régime douanier déclaré pour ces équipements : admission temporaire.
     *
     * <p>Distinct de {@link #equipementsPresents}, et pas redondant avec lui : du matériel peut
     * être nécessaire au marché sans entrer sous admission temporaire — importé définitivement,
     * ou déjà sur le territoire. C'est l'admission temporaire qui crée une obligation de
     * réexportation, donc un suivi à la clôture du crédit.
     *
     * <p>Nullable pour la même raison que son voisin : sur les dossiers antérieurs la question
     * n'a pas été posée, et {@code null} le dit sans qu'on ait à inventer une réponse.
     */
    @Column(name = "admission_temporaire")
    private Boolean admissionTemporaire;

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
