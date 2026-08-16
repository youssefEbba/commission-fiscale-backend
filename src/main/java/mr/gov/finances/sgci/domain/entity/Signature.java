package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.Role;

import java.time.Instant;

/**
 * Image de signature (PNG fond transparent) utilisée pour les documents générés côté client
 * (certificat de crédit, lettre d'adoption, utilisation). Au plus une version {@code active} par
 * couple (role, utilisateur) — versionnement identique au pattern GED (ancienne version désactivée,
 * pas supprimée, lors d'un remplacement).
 */
@Entity
@Table(name = "signature", indexes = {
        @Index(name = "idx_signature_role_user", columnList = "role,utilisateur_id"),
        @Index(name = "idx_signature_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Signature propre à un utilisateur précis. {@code null} = signature générique du rôle. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    private String nomAffiche;

    /** Clé objet dans le stockage (MinIO/local) — jamais d'URL publique, accès via flux authentifié. */
    @Column(nullable = false)
    private String objetMinio;

    private String contentType;
    private Long taille;
    private Integer largeurPx;
    private Integer hauteurPx;

    @Column(length = 64)
    private String checksumSha256;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    @Builder.Default
    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false)
    private Instant dateCreation;

    private String creePar;

    private Instant dateDesactivation;

    @PrePersist
    protected void onCreate() {
        if (dateCreation == null) {
            dateCreation = Instant.now();
        }
        if (active == null) {
            active = Boolean.TRUE;
        }
        if (version == null) {
            version = 1;
        }
    }
}
