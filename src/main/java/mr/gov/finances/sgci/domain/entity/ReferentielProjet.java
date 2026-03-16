package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.enums.StatutReferentielProjet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "referentiel_projet")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferentielProjet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String numero;

    private String nomProjet;
    private String administrateurProjet;
    private String referenceBciSecteur;

    private Instant dateDepot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutReferentielProjet statut = StatutReferentielProjet.EN_ATTENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_contractante_id", nullable = false)
    private AutoriteContractante autoriteContractante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convention_id", nullable = false)
    private Convention convention;

    private Long valideParUserId;
    private Instant dateValidation;

    @Column(length = 1000)
    private String motifRejet;

    @OneToMany(mappedBy = "referentielProjet", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentProjet> documents = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        dateDepot = Instant.now();
    }
    
}
