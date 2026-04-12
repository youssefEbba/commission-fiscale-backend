package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutReclamationCorrection;

import java.time.Instant;

@Entity
@Table(name = "reclamation_demande_correction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReclamationDemandeCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demande_correction_id", nullable = false)
    private DemandeCorrection demandeCorrection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(length = 4000, nullable = false)
    private String texte;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private StatutReclamationCorrection statut = StatutReclamationCorrection.SOUMISE;

    @Column(nullable = false)
    private Instant dateCreation;

    @Column(nullable = false)
    private Instant dateModification;

    private Instant dateTraitement;

    private Long traiteParUserId;

    @Column(length = 2000)
    private String motifReponse;

    /** Pièce justificative obligatoire à la création (hors table {@code document}). */
    @Column(length = 2000)
    private String pieceJointeChemin;

    @Column(length = 512)
    private String pieceJointeNomFichier;

    private Long pieceJointeTaille;

    private Instant pieceJointeDateUpload;

    /** Pièce jointe obligatoire en cas de rejet (DGTCP / Président), hors table {@code document}. */
    @Column(length = 2000)
    private String reponseRejetChemin;

    @Column(length = 512)
    private String reponseRejetNomFichier;

    private Long reponseRejetTaille;

    private Instant reponseRejetDateUpload;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        dateCreation = now;
        dateModification = now;
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = Instant.now();
    }
}
