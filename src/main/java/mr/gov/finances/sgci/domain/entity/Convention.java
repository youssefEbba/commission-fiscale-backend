package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutConvention;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "convention")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Convention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference;

    /** Référence projet (cadrage interne AC / bailleur), distincte de {@link #reference}. */
    @Column(length = 255)
    private String projectReference;

    @Column(nullable = false)
    private String intitule;

    private String bailleur;

    @Column(length = 1000)
    private String bailleurDetails;

    private LocalDate dateSignature;
    private LocalDate dateFin;

    private BigDecimal montantDevise;
    private BigDecimal montantMru;
    private String deviseOrigine;
    private BigDecimal tauxChange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutConvention statut = StatutConvention.EN_ATTENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_contractante_id", nullable = false)
    private AutoriteContractante autoriteContractante;

    /** Autorité dont l’utilisateur a créé l’enregistrement (souvent identique à {@link #autoriteContractante}). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par_autorite_contractante_id")
    private AutoriteContractante creeParAutoriteContractante;

    private Long valideParUserId;
    private Instant dateValidation;

    @Column(length = 1000)
    private String motifRejet;

    private Instant dateCreation;

    @OneToMany(mappedBy = "convention", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentConvention> documents = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        dateCreation = Instant.now();
    }
}
