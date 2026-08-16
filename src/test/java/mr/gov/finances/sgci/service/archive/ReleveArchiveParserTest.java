package mr.gov.finances.sgci.service.archive;

import mr.gov.finances.sgci.web.dto.archive.ReleveArchiveDto;
import mr.gov.finances.sgci.web.dto.archive.UtilisationArchiveDto;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérifie la lecture d'un relevé réel de l'ancienne application.
 *
 * <p>Le relevé contient des données fiscales nominatives : il n'est pas versionné. Le test lit le
 * chemin dans la propriété système {@code releve.archive.file} et se met en pause s'il est absent,
 * afin de ne jamais faire échouer une intégration continue.
 */
class ReleveArchiveParserTest {

    private static final String PROPRIETE_CHEMIN = "releve.archive.file";

    private final ReleveArchiveParser parser = new ReleveArchiveParser();

    private ReleveArchiveDto lireReleveReel() throws Exception {
        String chemin = System.getProperty(PROPRIETE_CHEMIN);
        Assumptions.assumeTrue(chemin != null && !chemin.isBlank(),
                "Relevé non fourni : passer -D" + PROPRIETE_CHEMIN + "=<chemin> pour activer ce test");
        Path fichier = Path.of(chemin);
        Assumptions.assumeTrue(Files.exists(fichier), "Relevé introuvable : " + chemin);

        return parser.parse(new MockMultipartFile(
                "fichier", fichier.getFileName().toString(),
                "application/vnd.ms-excel", Files.readAllBytes(fichier)));
    }

    @Test
    void lit_l_entete_et_les_montants_du_credit() throws Exception {
        ReleveArchiveDto releve = lireReleveReel();

        assertEquals("90600034", releve.getNif());
        assertEquals(0, releve.getCreditDouanier().compareTo(new BigDecimal("145829345.49")));
        assertEquals(0, releve.getCreditInterieur().compareTo(new BigDecimal("137336993.31")));
        assertEquals(0, releve.getMontantCreditImpot().compareTo(new BigDecimal("283166338.80")));
        assertEquals(0, releve.getTransfertCredit().compareTo(new BigDecimal("32136560.62")));

        // Le crédit d'impôt est bien la somme des deux enveloppes.
        assertEquals(0, releve.getCreditDouanier().add(releve.getCreditInterieur())
                .compareTo(releve.getMontantCreditImpot()));
    }

    @Test
    void lit_toutes_les_utilisations_et_retrouve_les_totaux_du_releve() throws Exception {
        ReleveArchiveDto releve = lireReleveReel();

        // 54 lignes physiques : les libellés ne sont pas uniques (UT 1, 2 et 3 figurent une fois en
        // TVA intérieure et une fois en douane, et il existe un « UT 20 BIS »).
        assertEquals(54, releve.getUtilisations().size());

        // Contrôle décisif : les totaux recalculés doivent tomber sur ceux imprimés dans le relevé.
        assertEquals(0, releve.getTotalUtilisationsDouane().compareTo(new BigDecimal("74185124.10")));
        assertEquals(0, releve.getTotalUtilisationsInterieur().compareTo(new BigDecimal("137747704.62")));
    }

    @Test
    void ventile_les_taxes_et_deduit_leur_affectation() throws Exception {
        ReleveArchiveDto releve = lireReleveReel();

        UtilisationArchiveDto ut13 = releve.getUtilisations().stream()
                .filter(u -> u.getLibelle().startsWith("UT 13 "))
                .findFirst().orElseThrow();

        assertTrue(ut13.isDouaniere());
        // Les taxes prises en charge totalisent exactement le montant imputé au crédit.
        assertEquals(0, ut13.getMontant().compareTo(new BigDecimal("3971743.73")));
        assertEquals(0, ut13.getTotalPrisEnCharge().compareTo(new BigDecimal("3971743.73")));
        // TTI + RIF + IMF restent à la charge de l'entreprise.
        assertEquals(0, ut13.getTotalAPayer().compareTo(new BigDecimal("253246.01")));

        List<String> auCi = ut13.getLignesTaxe().stream()
                .filter(l -> "AU_CI".equals(l.getAffectation()))
                .map(l -> l.getCodeTaxe()).toList();
        assertEquals(List.of("DD", "TVA", "RS", "PC", "PSC", "TCO"), auCi);
    }

    @Test
    void une_utilisation_de_tva_interieure_n_a_pas_de_ventilation() throws Exception {
        ReleveArchiveDto releve = lireReleveReel();

        UtilisationArchiveDto ut1 = releve.getUtilisations().stream()
                .filter(u -> u.getLibelle().startsWith("UT 1 ") && !u.isDouaniere())
                .findFirst().orElseThrow();

        assertTrue(ut1.getLignesTaxe().isEmpty());
        assertEquals(0, ut1.getMontant().compareTo(new BigDecimal("50897")));
    }

    @Test
    void le_transfert_solde_integralement_le_credit_douanier() throws Exception {
        ReleveArchiveDto releve = lireReleveReel();

        // Le bloc « Détailles transfert » ventile tout le reliquat douanier :
        // 145 829 345,49 − 74 185 124,10 d'utilisations = 71 644 221,39 sortis du crédit.
        assertEquals(0, releve.getTotalTransfertSortant().compareTo(new BigDecimal("71644221.39")));

        // D'où un solde douanier nul, conforme à ce qu'affiche le relevé.
        assertEquals(0, releve.getSoldeDouanierDeclare().compareTo(BigDecimal.ZERO));
        assertEquals(0, releve.getSoldeDouanierCalcule().compareTo(BigDecimal.ZERO));

        // Seule la part TVA (32 136 560,62) abonde le crédit intérieur ; les droits purs
        // (DD, RS, PC, PSC, TCO) sont soldés sans être transférables.
        assertEquals(0, releve.getTransfertCredit().compareTo(new BigDecimal("32136560.62")));
        assertEquals(0, releve.getSoldeInterieurDeclare().compareTo(new BigDecimal("31725849.31")));
        assertEquals(0, releve.getSoldeInterieurCalcule().compareTo(releve.getSoldeInterieurDeclare()));

        // Le relevé est donc intégralement cohérent : plus aucune anomalie.
        assertTrue(releve.getAnomalies().isEmpty(),
                () -> "anomalies inattendues : " + releve.getAnomalies());
    }

    @Test
    void extrait_la_reference_du_marche_sans_son_libelle_d_entete() throws Exception {
        ReleveArchiveDto releve = lireReleveReel();

        assertEquals("2021 S.H.C.C.W.Z. N°11", releve.getReferenceMarche());
    }
}
