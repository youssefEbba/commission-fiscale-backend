package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dqe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dqe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String numeroAAOI;
    private String projet;
    private String lot;

    private BigDecimal tauxTVA;

    private BigDecimal totalHT;
    private BigDecimal montantTVA;
    private BigDecimal totalTTC;

    @OneToMany(mappedBy = "dqe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DqeLigne> lignes = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id", nullable = false, unique = true)
    private DemandeCorrection demandeCorrection;
}
