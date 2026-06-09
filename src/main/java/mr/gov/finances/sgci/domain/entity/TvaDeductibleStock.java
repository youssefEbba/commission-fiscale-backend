package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.TvaDeductibleStockSource;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tva_deductible_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TvaDeductibleStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id", nullable = false)
    private CertificatCredit certificatCredit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisation_douane_id")
    private UtilisationDouaniere utilisationDouane;

    /**
     * {@link TvaDeductibleStockSource#UTILISATION_DOUANE} : liquidation d’une utilisation douanière ;
     * {@link TvaDeductibleStockSource#TRANSFERT_CREDIT} : reliquat TVA importation après transfert.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 32)
    private TvaDeductibleStockSource source;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal montantInitial;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal montantRestant;

    @Column(nullable = false)
    private Instant dateCreation;
}
