package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "decision_certificat_credit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionCertificatCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecisionCorrectionType decision;

    @Column(length = 1000)
    private String motifRejet;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "decision_certificat_credit_documents_demandes",
            joinColumns = @JoinColumn(name = "decision_certificat_credit_id"))
    @Column(name = "code_document", nullable = false, length = 64)
    @Builder.Default
    private Set<String> documentsDemandes = new HashSet<>();

    private Instant dateDecision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RejetTempStatus rejetTempStatus = RejetTempStatus.RESOLU;

    private Instant rejetTempResolvedAt;

    /**
     * Intervention de l'administrateur système à la place du rôle titulaire : visa posé à sa place,
     * ou rejet temporaire résolu à sa place. Nullable à dessein :
     * les décisions antérieures à l'ajout de la colonne restent à {@code null}, d'où la lecture
     * systématique via {@code Boolean.TRUE.equals(...)}.
     */
    @Column(name = "visa_par_admin")
    @Builder.Default
    private Boolean visaParAdmin = Boolean.FALSE;

    /** Motif saisi par l'administrateur lorsqu'il est intervenu à la place du rôle titulaire. */
    @Column(name = "motif_admin", length = 1000)
    private String motifAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id", nullable = false)
    private CertificatCredit certificatCredit;

    @OneToMany(mappedBy = "decisionCertificatCredit", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RejetTempResponse> rejetTempResponses;
}
