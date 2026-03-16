package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.MotifCloture;
import mr.gov.finances.sgci.domain.enums.TypeOperationCloture;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cloture_credit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClotureCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant dateProposition;

    private Instant dateCloture;

    @Enumerated(EnumType.STRING)
    private MotifCloture motif;

    @Enumerated(EnumType.STRING)
    private TypeOperationCloture typeOperation;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeRestant;

    private Boolean approuvee;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id")
    private CertificatCredit certificatCredit;
}
