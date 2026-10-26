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
 *       d'utilisation disparaîtrait au moment où la DGTCP clôture l'utilisation. C'est déjà la
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

    /**
     * Douane : la DGTCP a envoyé le dossier au Trésor.
     *
     * <p>C'est la règle posée par l'équipe de test : l'entreprise ne voit pas le certificat
     * d'utilisation avant que la DGTCP l'envoie au Trésor pour la quittance. L'attendre jusqu'à la
     * liquidation, comme le faisait la règle d'origine, le lui masquait pendant toute l'étape où il
     * circule.
     */
    private static final Set<StatutUtilisation> UTILISATION_DOUANE_OUVERTE =
            EnumSet.of(StatutUtilisation.ENVOYEE_AU_TRESOR, StatutUtilisation.QUITTANCES_ENREGISTREES,
                    StatutUtilisation.LIQUIDEE, StatutUtilisation.CLOTUREE);

    /**
     * TVA intérieure : la DGI a enregistré sa quittance.
     *
     * <p>Il n'y a pas d'envoi au Trésor dans ce circuit. Son pendant est l'étape où la direction
     * suivante — ici la DGI — s'est saisie du certificat émis.
     */
    private static final Set<StatutUtilisation> UTILISATION_TVA_OUVERTE =
            EnumSet.of(StatutUtilisation.QUITTANCE_DGI_ENREGISTREE, StatutUtilisation.APUREE,
                    StatutUtilisation.CLOTUREE);

    /**
     * Utilisation dont on ignore le circuit : le crédit est débité, dans l'un ou l'autre.
     *
     * <p>C'est la règle d'origine. Elle est plus stricte que chacune des deux autres, et c'est le
     * sens voulu du doute.
     */
    private static final Set<StatutUtilisation> UTILISATION_INDETERMINEE_OUVERTE =
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
            // EXTERIEUR et INTERIEUR sont les anciens noms des deux circuits : le référentiel y
            // retombe sur DOUANE et TVA_INTERIEURE, la règle d'ouverture aussi.
            case UTILISATION_CI_DOUANE, UTILISATION_CI_EXTERIEUR ->
                    statutDuDossier instanceof StatutUtilisation s && UTILISATION_DOUANE_OUVERTE.contains(s);
            case UTILISATION_CI_TVA_INTERIEURE, UTILISATION_CI_INTERIEUR ->
                    statutDuDossier instanceof StatutUtilisation s && UTILISATION_TVA_OUVERTE.contains(s);
            case UTILISATION_CI ->
                    statutDuDossier instanceof StatutUtilisation s && UTILISATION_INDETERMINEE_OUVERTE.contains(s);
            // Transfert, sous-traitance, clôture, convention, marché, projet, rattachement :
            // aucune pièce de sortie n'y est paramétrée, et un circuit ajouté plus tard doit faire
            // l'objet d'une décision plutôt que d'hériter d'un défaut ouvert.
            default -> false;
        };
    }
}
