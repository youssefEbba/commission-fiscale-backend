package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutSousTraitance;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sous_traitance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SousTraitance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Boolean contratEnregistre;

    @Column(precision = 19, scale = 4)
    private BigDecimal volumes;

    @Column(precision = 19, scale = 4)
    private BigDecimal quantites;

    private Instant dateAutorisation;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatutSousTraitance statut = StatutSousTraitance.DEMANDE;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id")
    private CertificatCredit certificatCredit;
}
