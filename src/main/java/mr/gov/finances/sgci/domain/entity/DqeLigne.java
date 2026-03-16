package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "dqe_ligne")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DqeLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String designation;
    private String unite;
    private BigDecimal quantite;
    private BigDecimal prixUnitaireHT;
    private BigDecimal montantHT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dqe_id", nullable = false)
    private Dqe dqe;
}
