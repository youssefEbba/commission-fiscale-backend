package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Quittance DGI attestant le paiement de la TVA intérieure.
 *
 * <p>Une seule quittance par utilisation : un nouveau dépôt remplace le précédent plutôt que de
 * s'y ajouter. Le justificatif est stocké sur le même canal que les quittances Trésor et référencé
 * ici par son chemin d'objet et son identifiant de document.
 */
@Entity
@Table(name = "quittance_dgi")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuittanceDgi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_quittance", nullable = false, length = 80)
    private String numeroQuittance;

    @Column(name = "date_quittance")
    private Instant dateQuittance;

    @Column(precision = 19, scale = 4)
    private BigDecimal montant;

    /** Clé de l'objet stocké (MinIO ou disque local selon la configuration). */
    @Column(name = "document_chemin")
    private String documentChemin;

    @Column(name = "document_nom_fichier", length = 255)
    private String documentNomFichier;

    /** Identifiant du {@link DocumentUtilisationCredit} correspondant, pour le téléchargement. */
    @Column(name = "document_id")
    private Long documentId;

    /** Auteur du dépôt, conservé même si le compte est désactivé plus tard. */
    @Column(name = "deposee_par", length = 120)
    private String deposeePar;

    @Column(name = "date_depot")
    private Instant dateDepot;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisation_credit_id", nullable = false, unique = true)
    private UtilisationCredit utilisationCredit;
}
