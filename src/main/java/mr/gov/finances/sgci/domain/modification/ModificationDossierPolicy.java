package mr.gov.finances.sgci.domain.modification;

import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;

import java.util.EnumSet;
import java.util.Set;

/**
 * Qui peut modifier quoi, à quel stade — l'arbitre unique.
 *
 * <p><b>Le problème qu'elle résout.</b> La réponse à cette question existait, mais écrite à six
 * endroits : {@code assertDemandeCorrectionContentEditable}, {@code assertUtilisationContentEditable},
 * {@code updateBrouillon}, et trois autres gardes propres à la convention, au marché et au
 * référentiel projet. Chacune avait son vocabulaire et son message, aucune ne connaissait les
 * autres. Personne ne pouvait répondre sans relire six fichiers — et là où une garde manquait,
 * c'étaient des soldes engagés qui se réécrivaient.
 *
 * <p><b>Pourquoi en code et non en paramètre.</b> Le sens d'une pièce se paramètre ; le moment où
 * un montant devient intouchable ne se choisit pas, il se lit dans le circuit. Confier cette table
 * à un écran reviendrait à demander à un administrateur de désigner un stade parmi douze, et un
 * stade mal saisi rouvrirait l'écriture sur un quota déjà consommé sans que rien ne le signale.
 * C'est le même raisonnement que pour {@code OuvertureDocumentsPolicy}.
 *
 * <p><b>Deux principes tiennent la table.</b>
 *
 * <ul>
 *   <li><b>Un solde ne se réécrit qu'avant engagement.</b> Dès qu'une utilisation existe, ou dès
 *       que le crédit est ouvert, les soldes n'appartiennent plus à la saisie mais à la
 *       consommation.</li>
 *   <li><b>Un stade inconnu refuse.</b> Un statut ajouté plus tard sans décision n'hérite pas d'un
 *       défaut ouvert. {@code ModificationDossierPolicyTest} parcourt les trois énumérations et
 *       rougit si l'un de leurs statuts n'a pas été tranché.</li>
 * </ul>
 *
 * <p><b>Trois méthodes typées plutôt qu'un {@code Object}.</b> {@code OuvertureDocumentsPolicy}
 * accepte un statut non typé et le teste par {@code instanceof} ; là-bas une erreur de type ferme
 * des pièces, défaut silencieux mais bénin. Ici, confondre un {@code StatutDemande} et un
 * {@code StatutCertificat} autoriserait ou refuserait une écriture financière. Le compilateur doit
 * porter cette erreur, pas l'exécution.
 *
 * <p><b>Ce que cette politique ne gouverne pas, délibérément.</b>
 *
 * <ul>
 *   <li>{@code ImportArchiveCreditService} réécrit montants, soldes et statut hors de tout graphe,
 *       <em>à dessein</em> : il reconstitue des dossiers historiques déjà clos. L'y brancher ferait
 *       cesser l'import légitime. Ne pas le faire « par cohérence ».</li>
 *   <li>La convention, le marché et le référentiel projet ont leurs propres énumérations de statut
 *       et gardent leurs gardes locales ({@code assertConventionEditable},
 *       {@code assertMarcheEditableForDocuments}, {@code assertReferentielEditable}). Les forcer
 *       ici élargirait la surface sans rien clarifier.</li>
 *   <li>Le <em>droit</em> d'agir — quel rôle, quel périmètre — reste aux services et aux
 *       {@code @PreAuthorize}. Cette politique répond au « à quel stade », pas au « par qui ».</li>
 *   <li>Conséquence concrète : {@code CertificatCreditService.updateBrouillon} garde sa propre
 *       garde, <em>plus stricte</em> que celle-ci. Elle réserve l'édition aux statuts
 *       {@code BROUILLON} et {@code ENVOYEE} parce que son écrivain est le déposant, alors que la
 *       politique autorise les montants pendant l'instruction — stade où la DGTCP renseigne le
 *       récapitulatif. Y déléguer assouplirait la règle au lieu de l'unifier. De même,
 *       {@code adminCorrectInfo} applique un régime fondé sur l'engagement et non sur le stade,
 *       sans quoi l'override ne pourrait plus rien réparer.</li>
 * </ul>
 */
public final class ModificationDossierPolicy {

    private ModificationDossierPolicy() {
    }

    // ── Demande de correction ──────────────────────────────────────────────────────────────────

    /**
     * Les stades où le contenu d'une demande de correction est encore à son déposant.
     *
     * <p>Reprend {@code DemandeCorrectionService.STATUTS_EDITABLE_CONTENT} à l'identique.
     */
    private static final Set<StatutDemande> DEMANDE_OUVERTE = EnumSet.of(
            StatutDemande.BROUILLON, StatutDemande.RECUE, StatutDemande.INCOMPLETE);

    /**
     * Le contenu d'une demande de correction est-il modifiable ?
     *
     * @param instructionCommencee vrai dès qu'un seul des quatre visas est posé. Un visa gèle le
     *                             contenu <em>même en {@code RECUE}</em> : une direction qui a
     *                             déjà statué ne doit pas voir le dossier changer sous elle.
     */
    public static Verdict demandeCorrection(StatutDemande statut, boolean instructionCommencee,
                                            ChampDossier champ) {
        if (statut == null || champ == null) {
            return Verdict.refus("Modification impossible : stade ou champ indéterminé");
        }
        if (champ == ChampDossier.SOLDE_ENGAGE) {
            // Une demande de correction ne porte pas de solde. Refuser plutôt que d'autoriser une
            // question qui n'a pas de sens ici.
            return Verdict.refus("Une demande de correction ne porte aucun solde");
        }
        // L'instruction est testée avant le statut, et cet ordre est celui du code d'origine : il
        // produit le message le plus utile. « Le traitement a déjà commencé » dit au déposant
        // pourquoi il ne peut plus rien changer ; « statut EN_EVALUATION » le laisserait le deviner.
        if (instructionCommencee) {
            return Verdict.refus(
                    "Modification impossible : le traitement par les services a déjà commencé");
        }
        if (!DEMANDE_OUVERTE.contains(statut)) {
            return Verdict.refus("Modification impossible dans le statut: " + statut);
        }
        return Verdict.ok();
    }

    // ── Certificat de crédit ───────────────────────────────────────────────────────────────────

    /** Avant toute prise en charge : le dossier appartient encore à son rédacteur. */
    private static final Set<StatutCertificat> CERTIFICAT_BROUILLON = EnumSet.of(
            StatutCertificat.BROUILLON, StatutCertificat.ENVOYEE);

    /**
     * En instruction : les montants s'y saisissent encore, les soldes y sont provisoires.
     *
     * <p>C'est le stade où la DGTCP renseigne le récapitulatif. Tant que le crédit n'est pas
     * ouvert, aucune utilisation ne s'y adosse et les soldes se recalculent sans dommage.
     */
    private static final Set<StatutCertificat> CERTIFICAT_EN_INSTRUCTION = EnumSet.of(
            StatutCertificat.EN_CONTROLE, StatutCertificat.INCOMPLETE,
            StatutCertificat.A_RECONTROLER, StatutCertificat.EN_VALIDATION_PRESIDENT,
            StatutCertificat.VALIDE_PRESIDENT, StatutCertificat.EN_OUVERTURE_DGTCP);

    /**
     * Le crédit est ouvert ou clos : montants et soldes sont engagés.
     *
     * <p>{@code MODIFIE} et {@code CLOTURE} figuraient ici depuis le début dans l'intention, et
     * nulle part dans le code : la garde historique testait {@code != OUVERT}, ce qui laissait
     * passer ces deux stades.
     */
    private static final Set<StatutCertificat> CERTIFICAT_ENGAGE = EnumSet.of(
            StatutCertificat.OUVERT, StatutCertificat.MODIFIE,
            StatutCertificat.CLOTURE, StatutCertificat.ANNULE);

    /**
     * Ce champ du certificat est-il modifiable à ce stade ?
     *
     * @param utilisationsExistent vrai si au moins une {@code UtilisationCredit} est adossée à ce
     *                             certificat. C'est le seul fait que la politique ne peut pas
     *                             établir seule, et c'est celui qui protège les soldes.
     */
    public static Verdict certificat(StatutCertificat statut, boolean utilisationsExistent,
                                     ChampDossier champ) {
        if (statut == null || champ == null) {
            return Verdict.refus("Modification impossible : stade ou champ indéterminé");
        }
        if (champ == ChampDossier.CONTENU_DECLARATIF) {
            // Sans effet financier : modifiable à tout stade, y compris par correction
            // administrateur sur un dossier clos.
            return Verdict.ok();
        }
        if (CERTIFICAT_BROUILLON.contains(statut)) {
            return Verdict.ok();
        }
        if (CERTIFICAT_ENGAGE.contains(statut)) {
            return Verdict.refus(champ == ChampDossier.SOLDE_ENGAGE
                    ? "Les soldes ne sont plus modifiables : le crédit est à l'état " + statut
                    : "Les montants du récapitulatif ne sont plus modifiables : le crédit est à "
                            + "l'état " + statut);
        }
        if (CERTIFICAT_EN_INSTRUCTION.contains(statut)) {
            if (utilisationsExistent) {
                return Verdict.refus("Modification impossible : des demandes d'utilisation "
                        + "existent déjà sur ce certificat (risque de désynchronisation des soldes)");
            }
            return Verdict.ok();
        }
        // Un statut ajouté sans décision : refuser plutôt qu'ouvrir.
        return Verdict.refus("Modification impossible dans le statut: " + statut);
    }

    // ── Utilisation de crédit ──────────────────────────────────────────────────────────────────

    /** Reprend {@code UtilisationCreditService.STATUTS_UTILISATION_EDITABLE} à l'identique. */
    private static final Set<StatutUtilisation> UTILISATION_OUVERTE = EnumSet.of(
            StatutUtilisation.BROUILLON, StatutUtilisation.DEMANDEE, StatutUtilisation.INCOMPLETE);

    /**
     * Le contenu d'une utilisation est-il modifiable ?
     *
     * @param rejetTemporaireOuvert exigé en {@code INCOMPLETE}. Un dossier incomplet n'est
     *                              réouvert à la saisie que par une demande de pièce explicite :
     *                              sans elle, le statut ne suffit pas.
     */
    public static Verdict utilisation(StatutUtilisation statut, boolean rejetTemporaireOuvert,
                                      ChampDossier champ) {
        if (statut == null || champ == null) {
            return Verdict.refus("Modification impossible : stade ou champ indéterminé");
        }
        if (champ == ChampDossier.SOLDE_ENGAGE) {
            // Les soldes vivent sur le certificat, pas sur l'utilisation qui les consomme.
            return Verdict.refus("Une utilisation ne porte aucun solde : ils sont sur le certificat");
        }
        if (!UTILISATION_OUVERTE.contains(statut)) {
            return Verdict.refus(
                    "Modification impossible : statut incompatible ou traitement déjà engagé");
        }
        if (statut == StatutUtilisation.INCOMPLETE && !rejetTemporaireOuvert) {
            return Verdict.refus(
                    "Modification impossible : aucun rejet temporaire n'est ouvert sur cette demande");
        }
        return Verdict.ok();
    }
}
