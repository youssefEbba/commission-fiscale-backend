package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeEmpreinte;

import java.time.Instant;

/**
 * Empreinte (PNG fond transparent) apposée sur les documents générés côté client : signature
 * manuscrite ou cachet, pour le certificat de crédit, la lettre d'adoption, le certificat
 * d'utilisation.
 *
 * <p>Au plus une version {@code active} par triplet (type, role, utilisateur) — versionnement
 * identique au pattern GED : l'ancienne version est désactivée, pas supprimée, lors d'un
 * remplacement. Le {@code type} fait partie de la clé : un cachet et une signature du même
 * utilisateur sont légitimement actifs en même temps.
 */
@Entity
@Table(name = "signature", indexes = {
        @Index(name = "idx_signature_role_user", columnList = "role,utilisateur_id"),
        @Index(name = "idx_signature_type_role_user", columnList = "type_empreinte,role,utilisateur_id"),
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

    /**
     * Nature de l'empreinte : signature manuscrite ou cachet.
     *
     * <p>Colonne <b>nullable à dessein</b> — voir {@code SignatureTypeEmpreinteMigration}. Les
     * lignes créées par l'application portent toujours une valeur ({@code @Builder.Default} plus le
     * repli de {@code @PrePersist}) ; seules les lignes antérieures à l'introduction du cachet
     * peuvent être nulles, le temps du rattrapage au démarrage.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "type_empreinte", length = 16)
    private TypeEmpreinte type = TypeEmpreinte.SIGNATURE;

    /** Empreinte propre à un utilisateur précis. {@code null} = empreinte générique du rôle. */
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
        if (type == null) {
            type = TypeEmpreinte.SIGNATURE;
        }
    }
}
