package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Correction administrateur (ADMIN_SI) d'informations d'une demande d'utilisation de crédit, à
 * tout moment. Patch partiel : seuls les champs non nuls sont appliqués. Champs spécifiques au
 * type DOUANIER ignorés si l'utilisation est de type TVA_INTERIEURE. Motif obligatoire, transmis
 * séparément et journalisé dans l'audit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCorrectionUtilisationRequest {

    @DecimalMin("0")
    private BigDecimal montant;

    /** Champs propres à {@code UtilisationDouaniere}. */
    @Size(max = 80)
    private String numeroDeclaration;
    @Size(max = 80)
    private String numeroBulletin;
    private Instant dateDeclaration;
}
