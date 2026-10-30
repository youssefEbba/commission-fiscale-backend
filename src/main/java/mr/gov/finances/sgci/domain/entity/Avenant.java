package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutAvenant;
import mr.gov.finances.sgci.domain.enums.TypeAvenant;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un avenant au marché d'un crédit d'impôt ouvert.
 *
 * <p>Cycle (équipe, octobre 2026) : déposé par l'entreprise ou l'autorité contractante, examiné par
 * la DGTCP, validé par le Président. Avec un impact financier ({@link TypeAvenant#FINANCIER}), une
 * demande de correction suit le circuit habituel ({@link #demandeCorrection}) et l'avenant n'est
 * adopté qu'avec elle. La DGTCP l'applique ensuite : le crédit et ses soldes sont recalculés, et
 * l'instantané avant/après reste ici.
 *
 * <p>Tous les champs ajoutés en octobre 2026 sont nullables : {@code ddl-auto} les ajoute sans valeur
 * par défaut, et aucun avenant n'existait encore.
 */
@Entity
@Table(name = "avenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String numero;

    private Instant dateSignature;

    @Enumerated(EnumType.STRING)
    private TypeAvenant type;

    /** Inscrite au registre de {@code ColonnesTexteSchemaMigration} : texte, et non ENUM MySQL figé. */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", length = 32)
    private StatutAvenant statut;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantModifie;

    @Column(columnDefinition = "TEXT")
    private String motif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id", nullable = false)
    private CertificatCredit certificatCredit;

    // ── dépôt ───────────────────────────────────────────────────────────────────────────────
    private Long deposeParUtilisateurId;
    private Instant dateDepot;

    /** Crédits proposés par le déposant, obligatoires pour un avenant financier. */
    @Column(precision = 19, scale = 4)
    private BigDecimal creditExterieurPropose;
    @Column(precision = 19, scale = 4)
    private BigDecimal creditInterieurPropose;

    /** La demande de correction déclenchée à la validation d'un avenant financier. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id")
    private DemandeCorrection demandeCorrection;

    // ── instruction ─────────────────────────────────────────────────────────────────────────
    private Long examineParId;
    private Instant dateExamen;
    private Long valideParId;
    private Instant dateValidation;
    private Long appliqueParId;
    private Instant dateApplication;

    @Column(length = 2000)
    private String motifRejet;

    // ── instantané de l'application ─────────────────────────────────────────────────────────
    @Column(precision = 19, scale = 4) private BigDecimal montantCordonAvant;
    @Column(precision = 19, scale = 4) private BigDecimal montantCordonApres;
    @Column(precision = 19, scale = 4) private BigDecimal montantTVAInterieureAvant;
    @Column(precision = 19, scale = 4) private BigDecimal montantTVAInterieureApres;
    @Column(precision = 19, scale = 4) private BigDecimal soldeCordonAvant;
    @Column(precision = 19, scale = 4) private BigDecimal soldeCordonApres;
    @Column(precision = 19, scale = 4) private BigDecimal soldeTVAAvant;
    @Column(precision = 19, scale = 4) private BigDecimal soldeTVAApres;
    @Column(precision = 19, scale = 4) private BigDecimal tvaImportationRestanteAvant;
    @Column(precision = 19, scale = 4) private BigDecimal tvaImportationRestanteApres;

    public boolean estFinancier() {
        return type == TypeAvenant.FINANCIER;
    }
}
