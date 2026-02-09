package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "utilisation_credit")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type_utilisation", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public abstract class UtilisationCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_utilisation", insertable = false, updatable = false)
    private TypeUtilisation type;

    private Instant dateDemande;

    @Column(precision = 19, scale = 4)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    private StatutUtilisation statut = StatutUtilisation.DEMANDEE;

    private Instant dateLiquidation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id", nullable = false)
    private CertificatCredit certificatCredit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;
}
