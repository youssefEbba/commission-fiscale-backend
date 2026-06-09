package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "rejet_temp_response")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejetTempResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String message;

    private String documentUrl;

    @Column(name = "code_document", length = 64)
    private String codeDocument;

    private Integer documentVersion;

    @Column(nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_certificat_credit_id")
    private DecisionCertificatCredit decisionCertificatCredit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_utilisation_credit_id")
    private DecisionUtilisationCredit decisionUtilisationCredit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_correction_id")
    private DecisionCorrection decisionCorrection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_transfert_credit_id")
    private DecisionTransfertCredit decisionTransfertCredit;
}
