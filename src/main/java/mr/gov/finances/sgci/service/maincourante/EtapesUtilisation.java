package mr.gov.finances.sgci.service.maincourante;

import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Quel rôle est attendu sur une utilisation de crédit, selon son statut et son type.
 *
 * <p><b>Cette table n'est pas inventée : elle est dérivée.</b> J'avais d'abord exclu les
 * utilisations de la main courante en affirmant qu'aucune règle enregistrée ne disait qui agit à
 * chaque étape. C'était faux. Deux sources la portent, et leur composition donne exactement ce qui
 * suit :
 *
 * <ul>
 *   <li>{@code UtilisationCreditWorkflow.TRANSITIONS} — depuis un statut, les statuts atteignables.</li>
 *   <li>{@code UtilisationCreditService.assertActorCanTransition} — pour un statut <em>cible</em> et
 *       un type, le rôle autorisé à l'atteindre.</li>
 * </ul>
 *
 * <p>Le rôle attendu sur un dossier est donc celui qui peut le faire avancer d'un cran. Les cibles
 * d'échappement — rejet, mise en incomplet, clôture — sont écartées : elles sont ouvertes presque
 * partout et ne constituent pas « l'étape suivante ».
 *
 * <p><b>Une table dérivée se périme.</b> {@code EtapesUtilisationTest} la confronte aux deux
 * sources : si le circuit change sans que cette table suive, le test rougit. C'est la condition
 * pour qu'une dérivation reste une dérivation et ne devienne pas une copie oubliée.
 */
public final class EtapesUtilisation {

    private EtapesUtilisation() {
    }

    /**
     * Circuit douanier : le dossier passe par la DGD, l'entreprise, la DGTCP puis le Président.
     *
     * <p>Lecture : au statut X, c'est à ce rôle de jouer.
     */
    private static final Map<StatutUtilisation, Set<Role>> DOUANE = new LinkedHashMap<>();

    /** Circuit TVA intérieure : la DGTCP instruit, le Président émet, la DGI encaisse. */
    private static final Map<StatutUtilisation, Set<Role>> TVA_INTERIEURE = new LinkedHashMap<>();

    static {
        // ── Douane ─────────────────────────────────────────────────────────────────────────────
        // Le déposant soumet son brouillon, et c'est lui qui répond à une demande de pièce.
        DOUANE.put(StatutUtilisation.BROUILLON, EnumSet.of(Role.ENTREPRISE, Role.SOUS_TRAITANT));
        // INCOMPLETE est volontairement absent : un dossier en attente de pièce figure déjà dans
        // la branche des rejets temporaires de la main courante, et l'inscrire ici afficherait
        // deux fois le même dossier au même déposant.
        // La DGD vérifie, annote le bulletin, puis vise.
        DOUANE.put(StatutUtilisation.DEMANDEE, EnumSet.of(Role.DGD));
        DOUANE.put(StatutUtilisation.A_RECONTROLER, EnumSet.of(Role.DGD));
        DOUANE.put(StatutUtilisation.EN_VERIFICATION, EnumSet.of(Role.DGD));
        // Visé : l'entreprise saisit le chèque. La DGD peut encore ré-annoter, la DGTCP liquider
        // un dossier ancien — les trois sont donc légitimement en attente de quelqu'un.
        DOUANE.put(StatutUtilisation.EN_CONTROLE_DGD, EnumSet.of(Role.DGD, Role.ENTREPRISE));
        DOUANE.put(StatutUtilisation.VISE, EnumSet.of(Role.ENTREPRISE, Role.DGTCP));
        // Chèque saisi : la DGTCP contrôle et transmet au Président.
        DOUANE.put(StatutUtilisation.CHEQUE_SAISI, EnumSet.of(Role.DGTCP));
        DOUANE.put(StatutUtilisation.TRANSMISE_AU_PRESIDENT, EnumSet.of(Role.PRESIDENT));
        // Le certificat émis ouvre le paiement au Trésor, puis la liquidation.
        DOUANE.put(StatutUtilisation.CERTIFICAT_EMIS, EnumSet.of(Role.DGTCP));
        DOUANE.put(StatutUtilisation.ENVOYEE_AU_TRESOR, EnumSet.of(Role.DGTCP));
        DOUANE.put(StatutUtilisation.QUITTANCES_ENREGISTREES, EnumSet.of(Role.DGTCP));
        // Liquidé : l'entreprise accuse réception, ce qui clôt le dossier.
        DOUANE.put(StatutUtilisation.LIQUIDEE, EnumSet.of(Role.ENTREPRISE, Role.SOUS_TRAITANT));

        // ── TVA intérieure ─────────────────────────────────────────────────────────────────────
        TVA_INTERIEURE.put(StatutUtilisation.BROUILLON, EnumSet.of(Role.ENTREPRISE, Role.SOUS_TRAITANT));
        // Hors émission du certificat, la DGTCP traite seule cette branche.
        TVA_INTERIEURE.put(StatutUtilisation.DEMANDEE, EnumSet.of(Role.DGTCP));
        TVA_INTERIEURE.put(StatutUtilisation.A_RECONTROLER, EnumSet.of(Role.DGTCP));
        TVA_INTERIEURE.put(StatutUtilisation.EN_VERIFICATION, EnumSet.of(Role.DGTCP));
        TVA_INTERIEURE.put(StatutUtilisation.VALIDEE, EnumSet.of(Role.PRESIDENT));
        // Le certificat est la pièce que l'entreprise présente à la DGI pour obtenir sa quittance.
        TVA_INTERIEURE.put(StatutUtilisation.CERTIFICAT_EMIS, EnumSet.of(Role.DGI));
        TVA_INTERIEURE.put(StatutUtilisation.QUITTANCE_DGI_ENREGISTREE, EnumSet.of(Role.DGTCP));
    }

    /** Les statuts qui attendent ce rôle, pour ce type d'utilisation. */
    public static Set<StatutUtilisation> statutsAttendantLeRole(TypeUtilisation type, Role role) {
        Map<StatutUtilisation, Set<Role>> table =
                type == TypeUtilisation.TVA_INTERIEURE ? TVA_INTERIEURE : DOUANE;
        Set<StatutUtilisation> out = EnumSet.noneOf(StatutUtilisation.class);
        table.forEach((statut, roles) -> {
            if (roles.contains(role)) {
                out.add(statut);
            }
        });
        return out;
    }

    /** Vrai si ce rôle intervient quelque part dans l'un des deux circuits. */
    public static boolean intervientDansLeCircuit(Role role) {
        return !statutsAttendantLeRole(TypeUtilisation.DOUANIER, role).isEmpty()
                || !statutsAttendantLeRole(TypeUtilisation.TVA_INTERIEURE, role).isEmpty();
    }

    /** Le libellé du geste attendu, pour la colonne « action » de la main courante. */
    public static String action(TypeUtilisation type, StatutUtilisation statut) {
        if (statut == StatutUtilisation.BROUILLON) {
            return "Soumettre la demande d'utilisation";
        }
        if (statut == StatutUtilisation.INCOMPLETE) {
            return "Compléter le dossier et le re-soumettre";
        }
        if (type == TypeUtilisation.TVA_INTERIEURE) {
            return switch (statut) {
                case DEMANDEE, A_RECONTROLER, EN_VERIFICATION -> "Vérifier et valider l'utilisation";
                case VALIDEE -> "Émettre le certificat d'utilisation";
                case CERTIFICAT_EMIS -> "Enregistrer la quittance DGI";
                case QUITTANCE_DGI_ENREGISTREE -> "Apurer la TVA intérieure";
                default -> "Traiter l'utilisation";
            };
        }
        return switch (statut) {
            case DEMANDEE, A_RECONTROLER, EN_VERIFICATION -> "Vérifier le bulletin de liquidation";
            case EN_CONTROLE_DGD -> "Viser le bulletin, ou saisir le chèque";
            case VISE -> "Saisir le chèque de la part à payer";
            case CHEQUE_SAISI -> "Contrôler et transmettre au Président";
            case TRANSMISE_AU_PRESIDENT -> "Émettre le certificat d'utilisation";
            case CERTIFICAT_EMIS -> "Envoyer au Trésor";
            case ENVOYEE_AU_TRESOR -> "Enregistrer les quittances du Trésor";
            case QUITTANCES_ENREGISTREES -> "Liquider l'utilisation";
            case LIQUIDEE -> "Accuser réception pour clôturer";
            default -> "Traiter l'utilisation";
        };
    }
}
