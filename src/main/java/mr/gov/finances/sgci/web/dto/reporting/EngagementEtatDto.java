package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.AxeEngagement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Ce que l'État a engagé en crédit d'impôt, ce qui en a été consommé, ce qu'il en reste.
 *
 * <p>Remplace la question que le reporting savait répondre — « combien d'objets par statut » — par
 * celle qu'on lui pose réellement. Le détail des trois enveloppes est dans
 * {@link EngagementLigneDto}.
 *
 * <p><b>La somme des lignes fait le total, toujours.</b> Deux lignes y veillent, et elles doivent
 * rester visibles à l'écran : « bailleur non renseigné » — la convention n'en porte pas — et
 * « hors rattachement » — le certificat n'a pas de demande de correction d'origine, donc ni
 * convention, ni autorité, ni bailleur. Les masquer donnerait un tableau dont les lignes ne font
 * pas le total, et la Commission cesserait de le croire. Leur taille est elle-même une mesure de la
 * qualité des données.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EngagementEtatDto {

    private AxeEngagement axe;

    @Builder.Default
    private List<EngagementLigneDto> lignes = new ArrayList<>();

    /** Le total, calculé sur le même périmètre et non comme somme des lignes rendues. */
    private EngagementLigneDto total;

    /**
     * {@code Σ (montantCordon − (b + c + d))}, qui devrait être nul.
     *
     * <p>L'invariant de ventilation n'est contrôlé que lorsque {@code montantCordon > 0} : les
     * dossiers à cordon nul et les reprises d'archive y échappent. Un écart non nul signale un
     * récapitulatif non ventilé, pas une erreur de calcul.
     */
    private BigDecimal ecartVentilation;

    /** Nombre de certificats contribuant à {@link #ecartVentilation}. */
    private long nbEcartVentilation;

    /** {@code consommé(soldes) − consommé(mouvements)} sur l'ensemble du périmètre. */
    private BigDecimal ecartMouvements;

    /**
     * Certificats dont le restant dépasse l'accordé.
     *
     * <p>Légitime : le report à nouveau de TVA intérieure peut faire remonter un solde au-dessus de
     * l'enveloppe. Compté, jamais écrêté à zéro — un chiffre rogné en silence est pire qu'un chiffre
     * surprenant.
     */
    private long nbRestantSuperieurAccorde;

    /** Vrai si l'appelant a restreint le périmètre par autorité ou par entreprise. */
    private boolean filtersApplied;
}
