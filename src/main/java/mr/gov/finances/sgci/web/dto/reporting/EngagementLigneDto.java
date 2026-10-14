package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.FiabiliteAgregat;

import java.math.BigDecimal;

/**
 * Une ligne de la ventilation de l'engagement : ce qui a été accordé, ce qu'il en reste.
 *
 * <p><b>Trois enveloppes, et non deux.</b> C'est le point que l'intuition rate. À l'ouverture du
 * crédit, {@code soldeCordon} est initialisé aux seuls droits et taxes hors TVA — b + c — alors que
 * {@code montantCordon} vaut b + c + d. Un {@code montantCordon − soldeCordon} affiche donc une
 * consommation égale à la TVA à l'import <em>sur un certificat ouvert et jamais utilisé</em>. La
 * TVA à l'import est suivie séparément, dans son propre quota, et c'est pourquoi elle a ici sa
 * propre enveloppe.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EngagementLigneDto {

    /** Identifiant de l'axe, ou l'une des clés conventionnelles de regroupement. */
    private String cle;

    /** Ce que lit un humain : nom de l'autorité, raison sociale, référence de convention. */
    private String libelle;

    private long nbCertificats;

    // ── enveloppe 1 : droits et taxes de douane hors TVA (b + c) ───────────────────────────────

    private BigDecimal accordeCordonHorsTva;
    private BigDecimal restantCordonHorsTva;
    private BigDecimal consommeCordonHorsTva;

    // ── enveloppe 2 : TVA à l'import (d) ───────────────────────────────────────────────────────

    private BigDecimal accordeTvaImport;

    /**
     * Le quota restant, <b>stock de TVA déductible compris</b>.
     *
     * <p>Sans ce terme, tout transfert afficherait 100 % de TVA à l'import consommée : le transfert
     * met le quota à zéro et déverse le reliquat dans le stock déductible, qui n'est pas une
     * consommation mais une reclassification. La liquidation douanière fait de même.
     */
    private BigDecimal restantTvaImport;
    private BigDecimal consommeTvaImport;

    // ── enveloppe 3 : TVA intérieure ───────────────────────────────────────────────────────────

    private BigDecimal accordeTvaInterieure;
    private BigDecimal restantTvaInterieure;
    private BigDecimal consommeTvaInterieure;

    // ── totaux et qualification ────────────────────────────────────────────────────────────────

    private BigDecimal accordeTotal;
    private BigDecimal restantTotal;

    /**
     * Le consommé, mesuré sur les soldes.
     *
     * <p>Les soldes sont la source de vérité : c'est sur eux que le workflow refuse une utilisation,
     * et c'est eux que l'import d'archive renseigne. Mesurer sur les mouvements afficherait zéro
     * consommé sur tout le stock repris de l'ancienne application.
     */
    private BigDecimal consommeSoldes;

    /** Le même consommé, recalculé depuis les mouvements. Sert de contrôle, pas de référence. */
    private BigDecimal consommeMouvements;

    /** {@code consommeSoldes − consommeMouvements}. Jamais arrondi à zéro. */
    private BigDecimal ecartMouvements;

    /**
     * Dossiers de cette ligne portant une cause connue d'écart.
     *
     * <p>Une reprise de l'ancienne application — soldes importés sans mouvements — ou une
     * correction manuelle d'administrateur. C'est ce compte qui rend {@link #fiabilite} lisible :
     * {@code EXPLIQUE} signifie « une cause connue est présente dans cette ligne », et non
     * « l'écart est intégralement imputé » — imputer l'écart dossier par dossier demanderait un
     * calcul par certificat que ce lot ne fait pas.
     */
    private long nbDossiersRepriseOuCorriges;

    private FiabiliteAgregat fiabilite;
}
