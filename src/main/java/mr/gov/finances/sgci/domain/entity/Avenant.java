package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.TypeAvenant;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "avenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String numero;

    private Instant dateSignature;

    @Enumerated(EnumType.STRING)
    private TypeAvenant type;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantModifie;

    @Column(columnDefinition = "TEXT")
    private String motif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id", nullable = false)
    private CertificatCredit certificatCredit;
}
