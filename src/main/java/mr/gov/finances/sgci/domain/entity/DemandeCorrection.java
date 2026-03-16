package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutDemande;

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

    private Instant dateDepot;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_contractante_id", nullable = false)
    private AutoriteContractante autoriteContractante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

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

    @OneToOne(mappedBy = "demandeCorrection", cascade = CascadeType.ALL, orphanRemoval = true)
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
