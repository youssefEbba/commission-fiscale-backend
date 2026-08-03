package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDemandeCorrectionRequest {

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

    /** Intitulé libre du marché / convention (remplace la création de marché dans le wizard de correction). */
    private String intituleMarche;

    /** Crédit intérieur demandé (défaut 0). Si 0, la DGI est exclue du workflow. */
    private BigDecimal creditInterieur;

    /** Crédit extérieur / douanier demandé (défaut 0). Si 0, la DGD est exclue du workflow. */
    private BigDecimal creditExterieur;

    /**
     * Si {@code true}, statut {@code BROUILLON} : pas de notification, champs fiscaux optionnels jusqu'à soumission.
     */
    private Boolean brouillon;

    /** Ignoré si {@code brouillon != true} (sinon obligatoires en service). */
    private ModeleFiscalDto modeleFiscal;

    /** Ignoré si {@code brouillon != true}. */
    private DqeDto dqe;
}
