package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codeNomenclature;
    private String designation;

    @Column(precision = 19, scale = 4)
    private BigDecimal quantite;

    @Column(precision = 19, scale = 4)
    private BigDecimal valeurUnitaire;

    @Column(precision = 19, scale = 4)
    private BigDecimal valeurTotale;

    private String classification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feuille_evaluation_id")
    private FeuilleEvaluation feuilleEvaluation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id")
    private CertificatCredit certificatCredit;
}
