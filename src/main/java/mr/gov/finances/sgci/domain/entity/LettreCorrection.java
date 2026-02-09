package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "lettre_correction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LettreCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String numero;

    private Instant dateEmission;
    private Instant dateSignature;
    private Boolean signee;
    private Boolean notifiee;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feuille_evaluation_id", unique = true)
    private FeuilleEvaluation feuilleEvaluation;

    @OneToOne(mappedBy = "lettreCorrection", cascade = CascadeType.ALL, orphanRemoval = true)
    private CertificatCredit certificatCredit;
}
