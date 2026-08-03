package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Mise à jour du contenu d'une demande (même forme que la création, hors statut).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDemandeCorrectionRequest {

    @NotNull(message = "L'autorité contractante est obligatoire")
    private Long autoriteContractanteId;

    /**
     * Entreprise titulaire (obligatoire si {@link #groupementId} est absent).
     * Ignoré si {@code groupementId} est fourni (l'entreprise devient automatiquement le chef de file).
     */
    private Long entrepriseId;

    /**
     * Groupement porteur (alternative à {@link #entrepriseId}).
     * Si renseigné, le titulaire effectif du dossier est le chef de file du groupement.
     */
    private Long groupementId;

    @NotNull(message = "La convention est obligatoire")
    private Long conventionId;

    /** Optionnel : le marché n'est plus créé à la correction. Utiliser {@link #intituleMarche} à la place. */
    private Long marcheId;

    /** Intitulé libre du marché / convention. */
    private String intituleMarche;

    /** Crédit intérieur demandé (défaut 0). Si 0, la DGI est exclue du workflow. */
    private BigDecimal creditInterieur;

    /** Crédit extérieur / douanier demandé (défaut 0). Si 0, la DGD est exclue du workflow. */
    private BigDecimal creditExterieur;

    @NotNull(message = "Le modèle fiscal est obligatoire")
    private ModeleFiscalDto modeleFiscal;

    @NotNull(message = "Le DQE est obligatoire")
    private DqeDto dqe;
}
