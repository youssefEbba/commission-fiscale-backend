package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "dossier_ged")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DossierGed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id", nullable = false, unique = true)
    private DemandeCorrection demandeCorrection;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id", unique = true)
    private CertificatCredit certificatCredit;

    @Column(nullable = false)
    private Instant dateCreation;

    @PrePersist
    protected void onCreate() {
        if (dateCreation == null) {
            dateCreation = Instant.now();
        }
    }
}
