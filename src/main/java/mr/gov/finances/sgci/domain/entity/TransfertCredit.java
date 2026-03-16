package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transfert_credit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransfertCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant dateDemande;

    @Column(precision = 19, scale = 4)
    private BigDecimal montant;

    private Boolean operationsDouaneCloturees;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatutTransfert statut = StatutTransfert.DEMANDE;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id")
    private CertificatCredit certificatCredit;
}
