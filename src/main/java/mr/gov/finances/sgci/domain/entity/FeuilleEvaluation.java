package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "feuille_evaluation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeuilleEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantCordon;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantTVA;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantTotal;

    private Instant dateEvaluation;
    private Boolean signee;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id", unique = true)
    private DemandeCorrection demandeCorrection;

    @OneToMany(mappedBy = "feuilleEvaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Item> items = new ArrayList<>();

    @OneToOne(mappedBy = "feuilleEvaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    private LettreCorrection lettreCorrection;
}
