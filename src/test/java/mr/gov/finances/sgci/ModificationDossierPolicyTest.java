package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.modification.ChampDossier;
import mr.gov.finances.sgci.domain.modification.ModificationDossierPolicy;
import mr.gov.finances.sgci.domain.modification.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La table « qui modifie quoi, à quel stade » confrontée à ses propres sources.
 *
 * <p>Une table dérivée se périme : c'est la leçon d'{@code EtapesUtilisation}, dont le test la
 * confronte aux deux sources réelles du circuit. Même exigence ici, et le test qui compte le plus
 * est celui de couverture — {@link #toutStatutDeCertificatEstTranche()} et ses deux voisins. Un
 * statut ajouté à l'une des trois énumérations sans qu'on ait décidé de son régime tombe sinon dans
 * le {@code default}, et un refus par omission ressemble à un refus voulu.
 *
 * <p>Test pur : la politique n'interroge pas la base, donc aucun contexte Spring n'est nécessaire.
 */
class ModificationDossierPolicyTest {

    /** Le message du {@code default} — celui qu'aucun statut tranché ne doit produire. */
    private static final String REFUS_PAR_OMISSION = "Modification impossible dans le statut";

    // ── couverture : aucun statut laissé au hasard ─────────────────────────────────────────────

    @Test
    void toutStatutDeCertificatEstTranche() {
        for (StatutCertificat statut : StatutCertificat.values()) {
            for (ChampDossier champ : ChampDossier.values()) {
                Verdict v = ModificationDossierPolicy.certificat(statut, false, champ);
                assertThat(v.raison() == null || !v.raison().startsWith(REFUS_PAR_OMISSION))
                        .as("le statut %s / champ %s doit être tranché explicitement", statut, champ)
                        .isTrue();
            }
        }
    }

    @Test
    void toutStatutDeDemandeRecoitUnVerdict() {
        for (StatutDemande statut : StatutDemande.values()) {
            for (ChampDossier champ : ChampDossier.values()) {
                assertThat(ModificationDossierPolicy.demandeCorrection(statut, false, champ))
                        .as("le statut %s / champ %s doit recevoir un verdict", statut, champ)
                        .isNotNull();
            }
        }
    }

    @Test
    void toutStatutDUtilisationRecoitUnVerdict() {
        for (StatutUtilisation statut : StatutUtilisation.values()) {
            for (ChampDossier champ : ChampDossier.values()) {
                assertThat(ModificationDossierPolicy.utilisation(statut, false, champ))
                        .as("le statut %s / champ %s doit recevoir un verdict", statut, champ)
                        .isNotNull();
            }
        }
    }

    @Test
    void unStatutNulOuUnChampNulRefuse() {
        // Dans le doute, on n'écrit pas.
        assertThat(ModificationDossierPolicy.certificat(null, false, ChampDossier.SOLDE_ENGAGE)
                .autorise()).isFalse();
        assertThat(ModificationDossierPolicy.certificat(StatutCertificat.BROUILLON, false, null)
                .autorise()).isFalse();
        assertThat(ModificationDossierPolicy.demandeCorrection(null, false,
                ChampDossier.CONTENU_DECLARATIF).autorise()).isFalse();
        assertThat(ModificationDossierPolicy.utilisation(null, false,
                ChampDossier.CONTENU_DECLARATIF).autorise()).isFalse();
    }

    // ── le cœur : les soldes après engagement ──────────────────────────────────────────────────

    @Test
    void aucunSoldeNEstModifiableDesLeCreditOuvert() {
        for (StatutCertificat statut : List.of(StatutCertificat.OUVERT, StatutCertificat.MODIFIE,
                StatutCertificat.CLOTURE, StatutCertificat.ANNULE)) {
            assertThat(ModificationDossierPolicy
                    .certificat(statut, false, ChampDossier.SOLDE_ENGAGE).autorise())
                    .as("les soldes doivent être figés à l'état %s", statut)
                    .isFalse();
        }
    }

    @Test
    void modifieEtClotureEtaientLesDeuxStadesOublies() {
        // La garde historique testait « != OUVERT », ce qui laissait passer MODIFIE et CLOTURE.
        // Ce test est la cible exacte de la correction : il échoue sur l'ancienne règle.
        for (StatutCertificat statut : List.of(StatutCertificat.MODIFIE, StatutCertificat.CLOTURE)) {
            assertThat(ModificationDossierPolicy
                    .certificat(statut, false, ChampDossier.MONTANT_DE_REFERENCE).autorise())
                    .as("les montants doivent être figés à l'état %s", statut)
                    .isFalse();
        }
    }

    @Test
    void lesMontantsRestentSaisissablesPendantLInstruction() {
        // Le pendant : c'est le stade où la DGTCP renseigne le récapitulatif. Tout fermer
        // empêcherait l'instruction normale.
        for (StatutCertificat statut : List.of(StatutCertificat.EN_CONTROLE,
                StatutCertificat.A_RECONTROLER, StatutCertificat.VALIDE_PRESIDENT,
                StatutCertificat.EN_OUVERTURE_DGTCP)) {
            assertThat(ModificationDossierPolicy
                    .certificat(statut, false, ChampDossier.MONTANT_DE_REFERENCE).autorise())
                    .as("la saisie des montants doit rester possible à l'état %s", statut)
                    .isTrue();
        }
    }

    @Test
    void uneUtilisationExistanteFigeLesMontantsDesLInstruction() {
        assertThat(ModificationDossierPolicy.certificat(
                StatutCertificat.EN_CONTROLE, true, ChampDossier.MONTANT_DE_REFERENCE).autorise())
                .isFalse();
    }

    @Test
    void leContenuDeclaratifResteCorrigeableMemeSurUnDossierClos() {
        // C'est l'intérêt de la correction administrateur : un intitulé ou une date se rattrapent
        // sans toucher à l'argent. Le lui retirer ne protégerait rien.
        for (StatutCertificat statut : StatutCertificat.values()) {
            assertThat(ModificationDossierPolicy
                    .certificat(statut, true, ChampDossier.CONTENU_DECLARATIF).autorise())
                    .as("le contenu déclaratif doit rester corrigeable à l'état %s", statut)
                    .isTrue();
        }
    }

    // ── demande de correction ──────────────────────────────────────────────────────────────────

    @Test
    void unSeulVisaGeleLaDemandeMemeEnStatutRecue() {
        assertThat(ModificationDossierPolicy.demandeCorrection(
                StatutDemande.RECUE, true, ChampDossier.CONTENU_DECLARATIF).autorise())
                .as("une direction qui a statué ne doit pas voir le dossier changer sous elle")
                .isFalse();

        assertThat(ModificationDossierPolicy.demandeCorrection(
                StatutDemande.RECUE, false, ChampDossier.CONTENU_DECLARATIF).autorise())
                .isTrue();
    }

    @Test
    void laDemandeSeFermeDesLEvaluation() {
        for (StatutDemande statut : List.of(StatutDemande.RECEVABLE, StatutDemande.EN_EVALUATION,
                StatutDemande.EN_VALIDATION, StatutDemande.ADOPTEE, StatutDemande.REJETEE,
                StatutDemande.NOTIFIEE, StatutDemande.ANNULEE)) {
            assertThat(ModificationDossierPolicy
                    .demandeCorrection(statut, false, ChampDossier.CONTENU_DECLARATIF).autorise())
                    .as("le contenu doit être figé à l'état %s", statut)
                    .isFalse();
        }
    }

    // ── utilisation ────────────────────────────────────────────────────────────────────────────

    @Test
    void uneUtilisationIncompleteExigeUnRejetTemporaireOuvert() {
        assertThat(ModificationDossierPolicy.utilisation(
                StatutUtilisation.INCOMPLETE, false, ChampDossier.CONTENU_DECLARATIF).autorise())
                .as("le statut INCOMPLETE ne suffit pas : il faut une demande de pièce explicite")
                .isFalse();

        assertThat(ModificationDossierPolicy.utilisation(
                StatutUtilisation.INCOMPLETE, true, ChampDossier.CONTENU_DECLARATIF).autorise())
                .isTrue();
    }

    @Test
    void uneUtilisationEngageeNEstPlusModifiable() {
        for (StatutUtilisation statut : List.of(StatutUtilisation.VISE,
                StatutUtilisation.CHEQUE_SAISI, StatutUtilisation.CERTIFICAT_EMIS,
                StatutUtilisation.LIQUIDEE, StatutUtilisation.APUREE,
                StatutUtilisation.CLOTUREE)) {
            assertThat(ModificationDossierPolicy
                    .utilisation(statut, true, ChampDossier.CONTENU_DECLARATIF).autorise())
                    .as("une utilisation à l'état %s ne doit plus changer", statut)
                    .isFalse();
        }
    }

    @Test
    void niLaDemandeNiLUtilisationNePortentDeSolde() {
        // Les soldes vivent sur le certificat. Poser la question ailleurs doit refuser plutôt que
        // de renvoyer un « oui » dénué de sens.
        assertThat(ModificationDossierPolicy.demandeCorrection(
                StatutDemande.BROUILLON, false, ChampDossier.SOLDE_ENGAGE).autorise()).isFalse();
        assertThat(ModificationDossierPolicy.utilisation(
                StatutUtilisation.BROUILLON, false, ChampDossier.SOLDE_ENGAGE).autorise()).isFalse();
    }
}
