package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import mr.gov.finances.sgci.domain.enums.ModeApposition;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_document", nullable = false, length = 64)
    private String codeDocument;

    @Column(nullable = false)
    private String nomFichier;

    private String chemin;

    private Instant dateUpload;

    private Long taille;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id")
    private DemandeCorrection demandeCorrection;

    /**
     * Comment le document a été signé et cacheté, lorsqu'il porte une signature.
     *
     * <p>Colonnes nullables : {@code null} = non déclaré, ce qui est la vérité pour les pièces
     * antérieures et pour toutes celles que personne ne signe. Voir {@link ModeApposition} sur la
     * portée exacte de cette déclaration — elle documente, elle ne prouve pas.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_apposition", length = 24)
    private ModeApposition modeApposition;

    /** Auteur de la signature, renseigné au dépôt lorsqu'un mode d'apposition est déclaré. */
    @Column(name = "signataire_utilisateur_id")
    private Long signataireUtilisateurId;

}
