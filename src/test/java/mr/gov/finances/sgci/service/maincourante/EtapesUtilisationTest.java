package mr.gov.finances.sgci.service.maincourante;

import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.workflow.UtilisationCreditWorkflow;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La table des étapes reste fidèle au circuit dont elle est tirée.
 *
 * <p>{@link EtapesUtilisation} n'invente rien : elle compose le graphe des transitions et l'arbitre
 * des rôles du service. Mais une table dérivée se périme en silence — le circuit évolue, la table
 * reste, et la main courante se met à désigner le mauvais rôle ou à oublier une étape. Ce test est
 * la laisse qui l'empêche de dériver.
 *
 * <p>Il vérifie deux implications, dans les deux sens :
 * <ul>
 *   <li>tout statut qui a une suite dans le circuit attend quelqu'un — sinon un dossier s'arrête là
 *       sans figurer dans aucune liste de travail ;</li>
 *   <li>tout statut qui attend quelqu'un a bien une suite — sinon on réclame une action impossible.</li>
 * </ul>
 *
 * <p>Sans Spring et sans base : le circuit est une structure, il se vérifie comme telle.
 */
class EtapesUtilisationTest {

    /**
     * Les issues ouvertes presque partout : elles ne constituent pas « l'étape suivante ».
     *
     * <p>Le rejet reste possible à presque tout moment et ne constitue pas une étape suivante. La
     * clôture, elle, en est une : c'est par elle que le déposant accuse réception d'un dossier
     * liquidé.
     */
    private static final Set<StatutUtilisation> ISSUES = EnumSet.of(
            StatutUtilisation.REJETEE);

    private final UtilisationCreditWorkflow workflow = new UtilisationCreditWorkflow();

    @Test
    void aucuneEtapeNeReclameUneActionImpossible() {
        // Si un statut sans suite figurait dans la table, la main courante demanderait d'avancer
        // un dossier que le circuit considère comme arrivé au bout.
        for (StatutUtilisation statut : StatutUtilisation.values()) {
            if (!presentDansUneTable(statut)) {
                continue;
            }
            assertThat(aUneSuite(statut))
                    .as("le statut %s attend un rôle alors que le circuit ne lui donne aucune "
                            + "suite : on réclamerait une action impossible", statut)
                    .isTrue();
        }
    }

    @Test
    void laCouvertureDuCircuitEstExplicite() {
        // La liste est écrite en toutes lettres, exprès. Un statut ajouté au circuit fera rougir
        // ce test, ce qui oblige à décider s'il attend quelqu'un — plutôt qu'à le laisser tomber
        // silencieusement hors de toutes les listes de travail.
        Set<StatutUtilisation> couverts = EnumSet.noneOf(StatutUtilisation.class);
        for (StatutUtilisation statut : StatutUtilisation.values()) {
            if (presentDansUneTable(statut)) {
                couverts.add(statut);
            }
        }

        assertThat(couverts).containsExactlyInAnyOrder(
                StatutUtilisation.BROUILLON,
                StatutUtilisation.DEMANDEE,
                StatutUtilisation.A_RECONTROLER,
                StatutUtilisation.EN_VERIFICATION,
                StatutUtilisation.VISE,
                StatutUtilisation.EN_CONTROLE_DGD,
                StatutUtilisation.CHEQUE_SAISI,
                StatutUtilisation.TRANSMISE_AU_PRESIDENT,
                StatutUtilisation.CERTIFICAT_EMIS,
                StatutUtilisation.ENVOYEE_AU_TRESOR,
                StatutUtilisation.QUITTANCES_ENREGISTREES,
                StatutUtilisation.LIQUIDEE,
                StatutUtilisation.VALIDEE,
                StatutUtilisation.QUITTANCE_DGI_ENREGISTREE);
    }

    @Test
    void lesStatutsDAttenteDePieceSontCouvertsAilleurs() {
        // INCOMPLETE n'est pas dans la table, et c'est voulu : un dossier en attente de pièce
        // figure déjà dans la branche des rejets temporaires. L'inscrire ici afficherait deux fois
        // le même dossier au même déposant.
        assertThat(presentDansUneTable(StatutUtilisation.INCOMPLETE)).isFalse();
    }

    @Test
    void chaqueEtapeDonneUnLibelleDAction() {
        // La colonne « action » est la raison d'être de la main courante : un statut sans libellé
        // ramènerait l'écran à une liste de dossiers.
        for (TypeUtilisation type : TypeUtilisation.values()) {
            for (StatutUtilisation statut : StatutUtilisation.values()) {
                if (!attendUnRole(type, statut)) {
                    continue;
                }
                assertThat(EtapesUtilisation.action(type, statut))
                        .as("libellé d'action pour %s / %s", type, statut)
                        .isNotBlank()
                        .isNotEqualTo("Traiter l'utilisation");
            }
        }
    }

    @Test
    void lesDeuxCircuitsNattendentPasLesMemesRoles() {
        // La DGD n'intervient qu'en douane, la DGI qu'en TVA intérieure. Si les deux tables se
        // confondaient, chaque direction verrait le travail de l'autre.
        assertThat(EtapesUtilisation.statutsAttendantLeRole(TypeUtilisation.DOUANIER, Role.DGD))
                .isNotEmpty();
        assertThat(EtapesUtilisation.statutsAttendantLeRole(TypeUtilisation.TVA_INTERIEURE, Role.DGD))
                .isEmpty();
        assertThat(EtapesUtilisation.statutsAttendantLeRole(TypeUtilisation.TVA_INTERIEURE, Role.DGI))
                .isNotEmpty();
        assertThat(EtapesUtilisation.statutsAttendantLeRole(TypeUtilisation.DOUANIER, Role.DGI))
                .isEmpty();
    }

    @Test
    void unRoleHorsCircuitNaRienAYFaire() {
        assertThat(EtapesUtilisation.intervientDansLeCircuit(Role.DGB)).isFalse();
        assertThat(EtapesUtilisation.intervientDansLeCircuit(Role.AUTORITE_CONTRACTANTE)).isFalse();
        assertThat(EtapesUtilisation.intervientDansLeCircuit(Role.DGD)).isTrue();
        assertThat(EtapesUtilisation.intervientDansLeCircuit(Role.DGTCP)).isTrue();
    }

    // ── sondes du circuit réel ─────────────────────────────────────────────────────────────────

    /** Vrai si le circuit permet d'avancer depuis ce statut, autrement que par une issue. */
    private boolean aUneSuite(StatutUtilisation depuis) {
        for (StatutUtilisation vers : StatutUtilisation.values()) {
            if (vers == depuis || ISSUES.contains(vers)) {
                continue;
            }
            try {
                workflow.validateTransition(depuis, vers);
                return true;
            } catch (RuntimeException ignoree) {
                // Transition refusée par le circuit : on essaie la suivante.
            }
        }
        return false;
    }

    private boolean presentDansUneTable(StatutUtilisation statut) {
        for (TypeUtilisation type : TypeUtilisation.values()) {
            if (attendUnRole(type, statut)) {
                return true;
            }
        }
        return false;
    }

    private boolean attendUnRole(TypeUtilisation type, StatutUtilisation statut) {
        for (Role role : Role.values()) {
            if (EtapesUtilisation.statutsAttendantLeRole(type, role).contains(statut)) {
                return true;
            }
        }
        return false;
    }
}
