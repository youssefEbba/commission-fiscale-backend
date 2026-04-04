package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeDocument;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "decision_correction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionCorrection {

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
    @CollectionTable(name = "decision_correction_documents_demandes",
            joinColumns = @JoinColumn(name = "decision_correction_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false)
    @Builder.Default
    private Set<TypeDocument> documentsDemandes = new HashSet<>();

    private Instant dateDecision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RejetTempStatus rejetTempStatus = RejetTempStatus.RESOLU;

    private Instant rejetTempResolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id", nullable = false)
    private DemandeCorrection demandeCorrection;

    @OneToMany(mappedBy = "decisionCorrection", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RejetTempResponse> rejetTempResponses;
}
