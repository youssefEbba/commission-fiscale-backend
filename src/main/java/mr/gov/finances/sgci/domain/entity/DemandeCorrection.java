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

    private Instant dateCreation;
    private Instant dateModification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_contractante_id", nullable = false)
    private AutoriteContractante autoriteContractante;

    @OneToMany(mappedBy = "demandeCorrection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Document> documents = new ArrayList<>();

    @OneToOne(mappedBy = "demandeCorrection", cascade = CascadeType.ALL, orphanRemoval = true)
    private FeuilleEvaluation feuilleEvaluation;

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
