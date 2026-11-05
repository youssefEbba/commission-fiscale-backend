package mr.gov.finances.sgci.service.reporting.export;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Lisibilité des exports : nombres du PDF au format français, noms de feuilles Excel propres. */
class ExportMiseEnFormeTest {

    @Test
    void lePdfEcritLesNombresAuFormatFrancais() {
        assertThat(PdfReportWriter.enFrancais("734746.00")).isEqualTo("734 746,00");
        assertThat(PdfReportWriter.enFrancais("126619192.10")).isEqualTo("126 619 192,10");
        assertThat(PdfReportWriter.enFrancais("-1500.5")).isEqualTo("-1 500,5");
        assertThat(PdfReportWriter.enFrancais("Montant total immobilisé : 0.00. C'est ce chiffre"))
                .isEqualTo("Montant total immobilisé : 0,00. C'est ce chiffre");
        assertThat(PdfReportWriter.enFrancais("12.50 %")).isEqualTo("12,50 %");
        // Identifiants, références et dates ne bougent pas.
        assertThat(PdfReportWriter.enFrancais("45")).isEqualTo("45");
        assertThat(PdfReportWriter.enFrancais("ARCHIVE-34/2023")).isEqualTo("ARCHIVE-34/2023");
        assertThat(PdfReportWriter.enFrancais("05/11/2026 01:53")).isEqualTo("05/11/2026 01:53");
    }

    @Test
    void unTableauDeVingtColonnesGardeSesMontantsEntiers() throws Exception {
        java.util.List<String> colonnes = new java.util.ArrayList<>(java.util.List.of("Clé", "Libellé"));
        java.util.List<String> ligne = new java.util.ArrayList<>(java.util.List.of("35", "DELEGATION GENERALE TAAZOUR A LA SOLIDARITE NATIONALE"));
        for (int i = 1; i <= 18; i++) {
            colonnes.add("Montant numéro " + i);
            ligne.add("126619192.10");
        }
        var dossier = new mr.gov.finances.sgci.service.reporting.model.ReportingDossier(
                new mr.gov.finances.sgci.service.reporting.model.ReportingDossier.EnTete(
                        "Reporting", "tout", "2026", "test", java.time.Instant.now(), "mention"),
                java.util.List.of(new mr.gov.finances.sgci.service.reporting.model.ReportingDossier.Section(
                        "Engagement", colonnes, java.util.List.of(ligne), null, null)));
        var sortie = new java.io.ByteArrayOutputStream();
        new PdfReportWriter().ecrire(dossier, sortie);

        try (var pdf = org.apache.pdfbox.Loader.loadPDF(sortie.toByteArray())) {
            String texte = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            // 18 montants entiers au format français, aucun coupé en « 126 619... ».
            assertThat(texte.split("126 619 192,10", -1)).hasSize(19);
            assertThat(texte).doesNotContain("126 619...").contains("(suite, 2/");
        }
    }

    @Test
    void lesNomsDeFeuillesExcelSontPropres() throws Exception {
        XlsxReportWriter writer = new XlsxReportWriter();
        Method nomUnique = XlsxReportWriter.class.getDeclaredMethod("nomUnique", String.class, Set.class);
        nomUnique.setAccessible(true);
        Set<String> deja = new HashSet<>();
        String nom = (String) nomUnique.invoke(writer, "Impact : demandes traitées et taux de validation", deja);
        assertThat(nom).doesNotContain("  ").hasSizeLessThanOrEqualTo(31).startsWith("Impact - demandes");
        assertThat((String) nomUnique.invoke(writer, "Crédits sans date de validité", deja)).isEqualTo("Crédits sans date de validité");
        try (SXSSFWorkbook classeur = new SXSSFWorkbook()) {
            classeur.createSheet(nom);   // Excel accepte le nom
        }
    }
}
