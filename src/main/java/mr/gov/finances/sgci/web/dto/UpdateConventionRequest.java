package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modification d'une convention par son autorité, avant la validation de la DGB.
 *
 * <p>Les champs de {@link CreateConventionRequest}, moins l'autorité titulaire : la changer
 * déplacerait la convention dans le périmètre d'une autre autorité, ce qui n'est pas une
 * correction de saisie. Remplacement complet, comme le formulaire qui l'envoie : un champ omis
 * est vidé.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateConventionRequest {

    /** Doit rester unique parmi les conventions. */
    @NotBlank(message = "La référence est obligatoire")
    private String reference;

    @NotBlank(message = "L'intitulé est obligatoire")
    private String intitule;

    /** Référence vers un bailleur enregistré ({@code GET /api/bailleurs}). Optionnel. */
    private Long bailleurId;

    /**
     * Tous les bailleurs, le principal en tête. Facultatif : absent, seul {@link #bailleurId} compte.
     * Présent, il remplace la liste, et son premier élément devient le bailleur principal.
     */
    private java.util.List<Long> bailleurIds;
    private LocalDate dateSignature;
    private LocalDate dateFin;
    private BigDecimal montantDevise;
    private BigDecimal montantMru;
    private String deviseOrigine;
    private BigDecimal tauxChange;

    /** Référence projet (optionnel). */
    private String projectReference;
}
