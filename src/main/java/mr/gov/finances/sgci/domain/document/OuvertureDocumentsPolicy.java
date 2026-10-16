package mr.gov.finances.sgci.domain.document;

import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;

import java.util.EnumSet;
import java.util.Set;

/**
 * À partir de quel moment les pièces de sortie d'un dossier deviennent communicables au déposant.
 *
 * <p><b>Pourquoi en code et non en paramètre.</b> Le sens d'une pièce — fournie ou produite — est un
 * attribut du document, et il se paramètre. Le moment où un dossier est « terminé » est un fait du
 * circuit : il se lit dans les graphes de transition, il ne se choisit pas. Le confier au
 * paramétrage reviendrait à demander à un administrateur de désigner un statut parmi dix-neuf, et
 * un statut mal saisi masquerait une pièce pour toujours sans que rien ne le signale.
 *
 * <p>Trois règles tiennent cette table :
 *
 * <ul>
 *   <li><b>Un rejet n'ouvre rien.</b> Un dossier rejeté n'a pas de pièce de sortie à communiquer.</li>
 *   <li><b>Les statuts terminaux sont inclus.</b> Sans {@code NOTIFIEE}, une pièce ouverte à
 *       l'adoption se refermerait à la notification ; sans {@code CLOTUREE}, le certificat
 *       d'utilisation disparaîtrait au moment où l'entreprise en accuse réception. C'est déjà la
 *       raison pour laquelle {@code CLOTUREE} figurait dans la règle d'origine.</li>
 *   <li><b>Un processus absent est fermé, pas ouvert.</b> Un circuit ajouté sans décision ne doit
 *       pas livrer ses pièces de sortie par défaut.</li>
 * </ul>
 */
public final class OuvertureDocumentsPolicy {

    /** La demande est adoptée : l'offre corrigée et la lettre d'adoption deviennent communicables. */
    private static final Set<StatutDemande> CORRECTION_OUVERTE =
            EnumSet.of(StatutDemande.ADOPTEE, StatutDemande.NOTIFIEE);

    /**
     * Le crédit est ouvert : le certificat signé devient communicable.
     *
     * <p>{@code VALIDE_PRESIDENT} n'y figure pas : entre la validation et l'ouverture, la DGTCP
     * saisit encore les montants. Le dossier n'est pas terminé tant que le crédit n'est pas ouvert.
     */
    private static final Set<StatutCertificat> MISE_EN_PLACE_OUVERTE =
            EnumSet.of(StatutCertificat.OUVERT, StatutCertificat.MODIFIE, StatutCertificat.CLOTURE);

    /** La règle d'origine, reprise telle quelle : liquidation, apurement, clôture. */
    private static final Set<StatutUtilisation> UTILISATION_OUVERTE =
            EnumSet.of(StatutUtilisation.LIQUIDEE, StatutUtilisation.APUREE,
                    StatutUtilisation.CLOTUREE);

    private OuvertureDocumentsPolicy() {
    }

    /**
     * Les pièces de sortie de ce dossier sont-elles communicables ?
     *
     * @param statutDuDossier le statut, typé selon le processus — {@code StatutDemande},
     *                        {@code StatutCertificat} ou {@code StatutUtilisation}. Un type qui ne
     *                        correspond pas au processus, ou un statut nul, ferme les pièces :
     *                        dans le doute on ne communique pas.
     */
    public static boolean ouvertesPour(ProcessusDocument processus, Object statutDuDossier) {
        if (processus == null || statutDuDossier == null) {
            return false;
        }
        return switch (processus) {
            case CORRECTION_OFFRE_FISCALE ->
                    statutDuDossier instanceof StatutDemande s && CORRECTION_OUVERTE.contains(s);
            case MISE_EN_PLACE_CI, MODIFICATION_CI ->
                    statutDuDossier instanceof StatutCertificat s && MISE_EN_PLACE_OUVERTE.contains(s);
            case UTILISATION_CI, UTILISATION_CI_DOUANE, UTILISATION_CI_TVA_INTERIEURE,
                 UTILISATION_CI_EXTERIEUR, UTILISATION_CI_INTERIEUR ->
                    statutDuDossier instanceof StatutUtilisation s && UTILISATION_OUVERTE.contains(s);
            // Transfert, sous-traitance, clôture, convention, marché, projet, rattachement :
            // aucune pièce de sortie n'y est paramétrée, et un circuit ajouté plus tard doit faire
            // l'objet d'une décision plutôt que d'hériter d'un défaut ouvert.
            default -> false;
        };
    }
}
