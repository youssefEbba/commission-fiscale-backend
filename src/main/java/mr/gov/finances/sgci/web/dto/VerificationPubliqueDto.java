package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Réponse de la vérification publique d'un document, scannée depuis son QR code.
 *
 * <p><b>Volontairement minimale.</b> Cette réponse est servie sans authentification, à quiconque
 * scanne le document au guichet du Trésor, de la DGI ou de la douane. Elle atteste l'authenticité et
 * l'état, rien de plus : <b>aucun montant, aucun solde, aucune donnée financière</b>. Le détail
 * complet reste derrière les écrans authentifiés.
 *
 * <p>Toujours rendue en 200, même pour un code inconnu ({@code trouve = false}) : un scanner a
 * besoin d'une réponse lisible, pas d'une erreur HTTP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationPubliqueDto {

    /** {@code false} si aucun document ne porte ce code. */
    private boolean trouve;

    /** Le code scanné, normalisé. */
    private String code;

    /** {@code CERTIFICAT_CREDIT} ou {@code CERTIFICAT_UTILISATION}, {@code null} si introuvable. */
    private String typeDocument;

    /** {@code true} si le document est authentique ET en cours de validité. */
    private boolean authentique;

    /** Libellé prêt à afficher, par exemple « Certificat authentique — valide ». */
    private String libelleEtat;

    /** {@code success}, {@code warning}, {@code muted} ou {@code destructive}. */
    private String severiteUi;

    private Instant dateEmission;

    /** Raison sociale du bénéficiaire : identifie le porteur sans rien révéler de ses finances. */
    private String entrepriseRaisonSociale;

    /** Statut métier, en clair. */
    private String statut;

    /** Précisions affichables, par exemple « Date de validité dépassée ». */
    private List<String> motifs;
}
