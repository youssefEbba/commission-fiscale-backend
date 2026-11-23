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

    /** Référence lisible ({@code ST-NNNN-MM-AAAA}) : c'est elle qu'on affiche, jamais l'id technique. */
    @Column(length = 64)
    private String reference;

    private Boolean contratEnregistre;

    @Column(precision = 19, scale = 4)
    private BigDecimal volumes;

    @Column(precision = 19, scale = 4)
    private BigDecimal quantites;

    private Instant dateAutorisation;

    /** Date de la demande (dépôt) : celle du récépissé et du reporting. */
    private Instant dateDemande;

    @PrePersist
    void dateDemandeParDefaut() {
        if (dateDemande == null) {
            dateDemande = Instant.now();
        }
    }

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatutSousTraitance statut = StatutSousTraitance.DEMANDE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sous_traitant_entreprise_id")
    private Entreprise sousTraitantEntreprise;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id")
    private CertificatCredit certificatCredit;
}
