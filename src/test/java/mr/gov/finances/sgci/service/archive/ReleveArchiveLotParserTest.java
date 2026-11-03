package mr.gov.finances.sgci.service.archive;

import mr.gov.finances.sgci.web.dto.archive.ReleveArchiveDto;
import mr.gov.finances.sgci.web.dto.archive.UtilisationArchiveDto;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lecture d'un export de période : découpage aux lignes « NIF », champs du marché, soldes recalculés.
 */
class ReleveArchiveLotParserTest {

    private final ReleveArchiveParser parser = new ReleveArchiveParser();

    @Test
    void chaqueLigneNifOuvreUnCreditLuCommeUnReleveAUnCredit() {
        MockMultipartFile fichier = new ExportPeriodeFixture()
                .bloc(ExportPeriodeFixture.avecTransfert("11100240", "10/2023", "0349/T/038/CPMP/MA/2022 DU 03/09/2022"))
                .bloc(ExportPeriodeFixture.douanier("20602865", "01/2026", "2030/T/016/CPMP/MPIMP/SNDP/2025 DU 06/1/26"))
                .bloc(ExportPeriodeFixture.interieurEcart("99999999", "16/2026", "0446/F/013/CPMP/MHA/AEPKIFFA/2025 DU N23/02/26"))
                .fichier("export_2026.xls");

        List<BlocReleve> blocs = parser.parseLot(fichier);

        assertThat(blocs).hasSize(3);
        assertThat(blocs).extracting(b -> b.releve().getNumeroCredit()).containsExactly("10/2023", "01/2026", "16/2026");
        assertThat(blocs.get(0).ligneDebut()).isEqualTo(1);
        assertThat(blocs.get(1).ligneDebut()).isEqualTo(blocs.get(0).ligneFin() + 1);

        ReleveArchiveDto transfert = blocs.get(0).releve();
        assertThat(transfert.getNif()).isEqualTo("11100240");
        assertThat(transfert.getNumeroMarche()).isEqualTo("0349/T/038/CPMP/MA/2022");
        assertThat(transfert.getDateMarche()).isEqualTo(Instant.parse("2022-09-03T00:00:00Z"));
        assertThat(transfert.getDureeMarche()).isEqualTo("75 JOURS");
        assertThat(transfert.getTotalTransfertSortant()).isEqualByComparingTo("2250380");
        assertThat(transfert.getSoldeDouanierCalcule()).isEqualByComparingTo("0");
        assertThat(transfert.getSoldeInterieurCalcule()).isEqualByComparingTo("2430379.73");
        // Rejeu : le transfert entre au stock, la 1re utilisation intérieure le déduit en entier
        // (net négatif → report à nouveau), la 2e s'impute sur le solde. Sans importation, on
        // retrouve exactement le solde du fichier.
        assertThat(transfert.getStockTvaEntrees()).isEqualByComparingTo("1024880");
        assertThat(transfert.getStockTvaRestant()).isEqualByComparingTo("0");
        assertThat(transfert.getUtilisations().get(0).getTvaDeductibleUtilisee()).isEqualByComparingTo("1024880");
        assertThat(transfert.getUtilisations().get(0).getReportANouveau()).isEqualByComparingTo("993017.93");
        assertThat(transfert.getUtilisations().get(1).getTvaDeductibleUtilisee()).isEqualByComparingTo("0");
        assertThat(transfert.getUtilisations().get(1).getCreditInterieurUtilise()).isEqualByComparingTo("15448.28");
        assertThat(transfert.getUtilisations()).hasSize(2).noneMatch(UtilisationArchiveDto::isDouaniere);
        assertThat(transfert.getAnomalies()).isEmpty();

        // Après le transfert, il ne reste ni droits ni TVA d'importation.
        assertThat(transfert.getTvaImportation()).isEqualByComparingTo("1024880");
        assertThat(transfert.getSoldeTvaImportationCalcule()).isEqualByComparingTo("0");
        assertThat(transfert.getSoldeDroitsCalcule()).isEqualByComparingTo("0");

        ReleveArchiveDto douanier = blocs.get(1).releve();
        // Ventilation par taxe : b = DD + RS + PC + PSC + TT, c = TCO, d = TVADD ; b + c + d = crédit douanier.
        assertThat(douanier.getDroitsEtTaxesHorsTva()).isEqualByComparingTo("1366690.15");
        assertThat(douanier.getTaxesConsommation()).isEqualByComparingTo("87000");
        assertThat(douanier.getTvaImportation()).isEqualByComparingTo("1262672.30");
        // d′ = d − TVA imputée en douane : c'est aussi le « Solde » TVADD de l'export (928 852,94).
        assertThat(douanier.getSoldeTvaImportationCalcule()).isEqualByComparingTo("928852.94");
        assertThat(douanier.getSoldeDroitsCalcule().add(douanier.getSoldeTvaImportationCalcule()))
                .isEqualByComparingTo(douanier.getSoldeDouanierCalcule());
        assertThat(douanier.getDateMarche()).as("année sur deux chiffres, jour et mois sur un").isEqualTo(Instant.parse("2026-01-06T00:00:00Z"));
        assertThat(douanier.getSoldeDouanierCalcule()).isEqualByComparingTo("1999332.09");
        // L'importation verse sa TVA au stock ; aucune utilisation intérieure ne la consomme.
        assertThat(douanier.getStockTva()).singleElement().satisfies(s -> {
            assertThat(s.getSource()).isEqualTo("UTILISATION_DOUANE");
            assertThat(s.getIndexUtilisation()).isZero();
            assertThat(s.getMontantRestant()).isEqualByComparingTo("333819.36");
        });
        assertThat(douanier.getSoldeInterieurCalcule()).isEqualByComparingTo("1129918.12");
        assertThat(douanier.getUtilisations()).singleElement().satisfies(u -> {
            assertThat(u.isDouaniere()).isTrue();
            assertThat(u.getTotalPrisEnCharge()).isEqualByComparingTo("717030.36");
            assertThat(u.getTotalAPayer()).isEqualByComparingTo("53427.42");
        });
        assertThat(douanier.getAnomalies()).isEmpty();

        ReleveArchiveDto ecart = blocs.get(2).releve();
        assertThat(ecart.getNumeroMarche()).isEqualTo("0446/F/013/CPMP/MHA/AEPKIFFA/2025");
        assertThat(ecart.getDateMarche()).isEqualTo(Instant.parse("2026-02-23T00:00:00Z"));
        assertThat(ecart.getSoldeInterieurCalcule()).isEqualByComparingTo("400000");
        assertThat(ecart.getAnomalies()).singleElement().asString()
                .contains("Solde intérieur").contains("450000").contains("400000").contains("recalculé");
    }

    @Test
    void leStockDeTvaEstDeduitAvantLeCreditInterieurEtLeDepassementEstPayeParLEntreprise() {
        ExportPeriodeFixture.Bloc modele = ExportPeriodeFixture.douanier("20602865", "01/2026", "2030/T/016/CPMP/X/2025 DU 06/10/2025");
        java.util.List<ExportPeriodeFixture.Utilisation> utils = new java.util.ArrayList<>(modele.utilisations());
        utils.add(new ExportPeriodeFixture.Utilisation("10/03/2026", "UT 2 | 2/01/2026", "500000", ""));
        utils.add(new ExportPeriodeFixture.Utilisation("11/03/2026", "UT 3 | 3/01/2026", "20000", ""));
        ExportPeriodeFixture.Bloc bloc = new ExportPeriodeFixture.Bloc(modele.nif(), modele.referenceMarche(), modele.duree(),
                modele.dateCredit(), modele.numeroCredit(), modele.montantMarche(), "2.816.362,45", modele.transfert(),
                modele.creditDouanier(), "100.000,00", modele.soldeDouanier(), "0", modele.ligneTransfert(), utils, modele.totaux());

        ReleveArchiveDto r = parser.parseLot(new ExportPeriodeFixture().bloc(bloc).fichier("paiement.xls")).get(0).releve();

        // UT 2 : 500 000 − stock 333 819,36 = 166 180,64 ; le solde (100 000) n'en couvre qu'une part.
        UtilisationArchiveDto ut2 = r.getUtilisations().get(1);
        assertThat(ut2.getTvaDeductibleUtilisee()).isEqualByComparingTo("333819.36");
        assertThat(ut2.getTvaNette()).isEqualByComparingTo("166180.64");
        assertThat(ut2.getCreditInterieurUtilise()).isEqualByComparingTo("100000");
        assertThat(ut2.getPaiementEntreprise()).isEqualByComparingTo("66180.64");
        // UT 3 : plus de stock ni de solde, tout est payé par l'entreprise.
        assertThat(r.getUtilisations().get(2).getPaiementEntreprise()).isEqualByComparingTo("20000");
        assertThat(r.getSoldeInterieurCalcule()).isEqualByComparingTo("0");
        assertThat(r.getStockTvaRestant()).isEqualByComparingTo("0");
        assertThat(r.getPaiementEntrepriseTotal()).isEqualByComparingTo("86180.64");
    }

    @Test
    void unRelevéAUnCreditSeLitToujoursSurLaFeuilleEntiere() {
        MockMultipartFile fichier = new ExportPeriodeFixture()
                .bloc(ExportPeriodeFixture.douanier("20602865", "01/2026", "2030/T/016/CPMP/MPIMP/SNDP/2025 DU 06/10/2025"))
                .fichier("releve.xls");

        ReleveArchiveDto releve = parser.parse(fichier);

        assertThat(releve.getNumeroCredit()).isEqualTo("01/2026");
        assertThat(releve.getCreditDouanier()).isEqualByComparingTo("2716362.45");
        assertThat(releve.getUtilisations()).hasSize(1);
        assertThat(releve.getDateMarche()).isEqualTo(Instant.parse("2025-10-06T00:00:00Z"));
    }

    /**
     * Les quatre vrais exports, non versionnés : {@code -Dreleve.lot.dir=<dossier>}. Aucun bloc ne
     * doit être illisible ; les écarts entre soldes affichés et calculés sont listés pour contrôle.
     */
    @Test
    void vraisExportsDePeriode() throws IOException {
        String dossier = System.getProperty("releve.lot.dir");
        Assumptions.assumeTrue(dossier != null && !dossier.isBlank(), "releve.lot.dir non fourni");
        List<Path> fichiers;
        try (Stream<Path> s = Files.list(Path.of(dossier))) {
            fichiers = s.filter(p -> p.getFileName().toString().matches("export_utilisation_des_credits_impot_.*\\.xls"))
                    .sorted().toList();
        }
        for (Path p : fichiers) {
            List<BlocReleve> blocs = parser.parseLot(new MockMultipartFile("fichier", p.getFileName().toString(),
                    "application/vnd.ms-excel", Files.readAllBytes(p)));
            long illisibles = blocs.stream().filter(b -> b.releve().getNumeroCredit() == null).count();
            long avecEcart = blocs.stream().filter(b -> !b.releve().getAnomalies().isEmpty()).count();
            int utilisations = blocs.stream().mapToInt(b -> b.releve().getUtilisations().size()).sum();
            System.out.printf("%s : %d crédits, %d utilisations, %d avec anomalie(s), %d illisible(s)%n",
                    p.getFileName(), blocs.size(), utilisations, avecEcart, illisibles);
            blocs.stream().filter(b -> b.releve().getNumeroCredit() == null).forEach(b ->
                    System.out.printf("   N° de crédit illisible, lignes %d à %d (NIF %s)%n",
                            b.ligneDebut(), b.ligneFin(), b.releve().getNif()));
            blocs.stream().filter(b -> !b.releve().getAnomalies().isEmpty()).forEach(b ->
                    b.releve().getAnomalies().forEach(a ->
                            System.out.printf("   %s (l.%d) : %s%n", b.releve().getNumeroCredit(), b.ligneDebut(), a)));
            // Un crédit sans numéro ni date existe réellement dans l'export 2023 : il est signalé,
            // et l'opérateur les saisit dans le lot. Aucun bloc ne doit en revanche perdre ses montants.
            assertThat(illisibles).as(p.getFileName().toString()).isLessThanOrEqualTo(1);
            assertThat(blocs).allSatisfy(b -> {
                assertThat(b.releve().getNif()).isNotBlank();
                assertThat(b.releve().getCreditDouanier()).isNotNull();
                assertThat(b.releve().getCreditInterieur()).isNotNull();
            });
        }
    }
}
