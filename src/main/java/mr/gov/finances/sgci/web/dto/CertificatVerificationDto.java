package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.EtatVerificationCertificat;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Réponse légère pour la vérification d'un certificat via scan du code-barres ({@code numero}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatVerificationDto {

    /** {@code true} si un certificat correspond au numéro normalisé. */
    private boolean trouve;

    /** Numéro normalisé (trim, majuscules) — identique au code-barres attendu. */
    private String numero;

    private Long certificatId;
    private StatutCertificat statutCertificat;

    /**
     * État synthétique pour l'UI : badge Valide / Expiré / Clôturé / En cours / Inconnu.
     */
    private EtatVerificationCertificat etatVerification;

    /** Libellé français prêt à afficher (ex. « Certificat valide »). */
    private String libelleEtat;

    /**
     * Indication couleur front : {@code success} | {@code warning} | {@code destructive} | {@code muted}.
     */
    private String severiteUi;

    private Instant dateEmission;
    private Instant dateValidite;
    private boolean expire;

    private String entrepriseRaisonSociale;
    private Long marcheId;

    private BigDecimal soldeCordon;
    private BigDecimal soldeTVA;

    /** Éligibilité utilisation douane (certificat OUVERT/MODIFIE, transfert non exécuté, etc.). */
    private boolean utilisableDouane;

    /** Éligibilité utilisation TVA intérieure. */
    private boolean utilisableTVA;

    @Builder.Default
    private List<String> motifs = new ArrayList<>();
}
