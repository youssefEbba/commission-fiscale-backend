package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Quittance Trésor enregistrée par le DGTCP après traitement par le Trésor.
 * Plusieurs quittances peuvent être liées à une même utilisation douanière.
 */
@Entity
@Table(name = "quittance_tresor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuittanceTresor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numéro de la quittance émise par le Trésor. */
    @Column(length = 80)
    private String numeroQuittance;

    /** Date d'émission de la quittance. */
    private Instant dateQuittance;

    /** Montant figurant sur la quittance (MRU). */
    @Column(precision = 19, scale = 4)
    private BigDecimal montant;

    /** Référence du paiement / bordereau Trésor. */
    @Column(length = 120)
    private String referencePaiement;

    /** URL/chemin du justificatif dans le GED (scan de la quittance). */
    @Column(name = "document_chemin")
    private String documentChemin;

    /** Nom original du fichier justificatif uploadé. */
    @Column(name = "document_nom_fichier", length = 255)
    private String documentNomFichier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisation_douaniere_id", nullable = false)
    private UtilisationDouaniere utilisationDouaniere;
}
