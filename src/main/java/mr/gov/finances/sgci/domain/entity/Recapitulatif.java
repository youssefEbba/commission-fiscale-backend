package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "modele_fiscal_recapitulatif")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recapitulatif {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double creditExterieur;
    private Double creditInterieur;
    private Double creditTotal;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modele_fiscal_id", nullable = false, unique = true)
    private ModeleFiscal modeleFiscal;
}
