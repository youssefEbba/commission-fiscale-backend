package mr.gov.finances.sgci.service.reporting.model;

import java.time.Instant;
import java.util.List;

/**
 * Un état de reporting, sous une forme qu'aucun format ne connaît.
 *
 * <p><b>C'est ce modèle unique qui rend impossible la divergence entre l'écran et les fichiers.</b>
 * Le défaut classique d'un export est d'avoir sa propre requête : elle part juste, puis l'écran
 * évolue et le fichier ne suit pas, et plus personne ne sait lequel des deux chiffres croire. Ici
 * les trois formats — tableur, CSV, PDF — rendent le même objet, assemblé par le même service que
 * celui qui alimente les routes JSON.
 *
 * <p>Les valeurs sont déjà des chaînes, mises en forme une seule fois : les trois formats affichent
 * donc exactement la même chose. Les montants sont écrits sans séparateur de milliers et avec un
 * point décimal, pour que le rédacteur de tableur puisse les reconnaître comme des nombres et les
 * rendre sommables — un montant écrit en texte dans un classeur est un montant qu'on ne peut pas
 * additionner.
 */
public record ReportingDossier(EnTete enTete, List<Section> sections) {

    /**
     * Ce qui identifie l'état : qui l'a produit, quand, sur quel périmètre.
     *
     * <p>{@code mentionFiabilite} n'est pas décorative : un état signé qui tairait ses écarts ou ses
     * limites de calcul engagerait la Commission sur des chiffres qu'elle n'a pas pu apprécier.
     */
    public record EnTete(String titre, String perimetre, String fenetre,
                         String auteur, Instant genereLe, String mentionFiabilite) {
    }

    /**
     * Un tableau de l'état.
     *
     * @param mentionTroncature non nul quand la liste a été coupée — et il doit alors figurer dans
     *                          le fichier. Une liste tronquée en silence se lit comme une liste
     *                          complète, et fait conclure que le travail est fait.
     * @param note              une précision à imprimer sous le tableau : la définition d'une
     *                          durée, le sens d'un écart. Elle voyage avec le chiffre.
     */
    public record Section(String titre, List<String> colonnes, List<List<String>> lignes,
                          String mentionTroncature, String note) {
    }
}
