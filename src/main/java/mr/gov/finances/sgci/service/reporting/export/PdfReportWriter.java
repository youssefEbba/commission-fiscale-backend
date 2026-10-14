package mr.gov.finances.sgci.service.reporting.export;

import mr.gov.finances.sgci.service.reporting.model.ReportingDossier;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Rend un état en PDF, destiné à être imprimé et présenté en séance.
 *
 * <p>PDFBox, en Apache-2.0 comme POI : aucune obligation de licence à faire arbitrer, ce qui compte
 * pour un dépôt public dont le commanditaire est un ministère. iText 7, en AGPL-3.0, a été écarté
 * pour cette raison seule.
 *
 * <p><b>Ce PDF est francophone, et c'est une limite assumée.</b> PDFBox n'inverse pas le sens
 * d'écriture et ne lie pas les lettres arabes : un texte arabe y sortirait en caractères détachés
 * et dans le mauvais ordre, c'est-à-dire illisible. Produire un PDF arabe correct demande une
 * couche de mise en forme supplémentaire, et c'est un travail distinct. L'écran, lui, reste
 * bilingue.
 *
 * <p>Corollaire technique : les polices intégrées de PDFBox n'encodent que le jeu WinAnsi et
 * <b>lèvent une exception</b> sur tout caractère hors de ce jeu. Les accents français, les
 * guillemets « » et le tiret cadratin y sont ; l'espace fine insécable, les guillemets courbes et
 * l'arabe n'y sont pas. {@link #assainir} les remplace donc en amont, plutôt que de laisser
 * l'écriture échouer sur un caractère invisible — c'est le mode de panne le plus déroutant qu'on
 * puisse livrer.
 */
@Component
public class PdfReportWriter {

    private static final float MARGE = 40f;
    private static final float INTERLIGNE = 12f;
    private static final float TAILLE_TITRE = 13f;
    private static final float TAILLE_CORPS = 8f;
    private static final int LARGEUR_COLONNE_CARACTERES = 22;

    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    public void ecrire(ReportingDossier dossier, OutputStream sortie) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont normale = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont gras = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            Page page = new Page(document);
            ReportingDossier.EnTete e = dossier.enTete();
            page.titre(e.titre(), gras);
            page.ligne("P\u00e9rim\u00e8tre : " + e.perimetre(), normale);
            page.ligne("P\u00e9riode : " + e.fenetre(), normale);
            page.ligne("\u00c9dit\u00e9 par " + e.auteur()
                    + (e.genereLe() != null ? " le " + HORODATAGE.format(e.genereLe()) : ""), normale);
            page.saut();
            for (String morceau : decouper(e.mentionFiabilite(), 110)) {
                page.ligne(morceau, normale);
            }

            for (ReportingDossier.Section section : dossier.sections()) {
                page.saut();
                page.titre(section.titre(), gras);
                if (section.mentionTroncature() != null) {
                    page.ligne(section.mentionTroncature(), gras);
                }
                page.ligne(assembler(section.colonnes()), gras);
                for (List<String> valeurs : section.lignes()) {
                    page.ligne(assembler(valeurs), normale);
                }
                if (section.note() != null) {
                    page.saut();
                    for (String morceau : decouper(section.note(), 110)) {
                        page.ligne(morceau, normale);
                    }
                }
            }
            page.fermer();
            document.save(sortie);
        }
    }

    /** Une page en cours d'écriture, qui en ouvre une nouvelle quand elle est pleine. */
    private static final class Page {
        private final PDDocument document;
        private PDPageContentStream flux;
        private float y;

        private Page(PDDocument document) throws IOException {
            this.document = document;
            nouvelle();
        }

        private void nouvelle() throws IOException {
            if (flux != null) {
                flux.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            flux = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGE;
        }

        private void titre(String texte, PDFont police) throws IOException {
            ecrire(texte, police, TAILLE_TITRE);
        }

        private void ligne(String texte, PDFont police) throws IOException {
            ecrire(texte, police, TAILLE_CORPS);
        }

        private void saut() {
            y -= INTERLIGNE;
        }

        private void ecrire(String texte, PDFont police, float taille) throws IOException {
            if (y <= MARGE) {
                nouvelle();
            }
            flux.beginText();
            flux.setFont(police, taille);
            flux.newLineAtOffset(MARGE, y);
            flux.showText(assainir(texte));
            flux.endText();
            y -= taille + 4f;
        }

        private void fermer() throws IOException {
            if (flux != null) {
                flux.close();
                flux = null;
            }
        }
    }

    private static String assembler(List<String> valeurs) {
        StringBuilder sb = new StringBuilder();
        for (String v : valeurs) {
            String cellule = v != null ? v : "";
            if (cellule.length() > LARGEUR_COLONNE_CARACTERES) {
                cellule = cellule.substring(0, LARGEUR_COLONNE_CARACTERES - 1) + "\u2026";
            }
            sb.append(String.format("%-" + LARGEUR_COLONNE_CARACTERES + "s", cellule));
        }
        return sb.toString();
    }

    private static List<String> decouper(String texte, int largeur) {
        List<String> out = new ArrayList<>();
        if (texte == null || texte.isBlank()) {
            return out;
        }
        StringBuilder courante = new StringBuilder();
        for (String mot : texte.split(" ")) {
            if (courante.length() + mot.length() + 1 > largeur) {
                out.add(courante.toString());
                courante.setLength(0);
            }
            if (courante.length() > 0) {
                courante.append(' ');
            }
            courante.append(mot);
        }
        if (courante.length() > 0) {
            out.add(courante.toString());
        }
        return out;
    }

    /**
     * Remplace ce que les polices intégrées ne savent pas encoder.
     *
     * <p>Elles lèvent sur tout caractère hors WinAnsi. Un état qui échoue à l'écriture à cause d'une
     * espace fine insécable invisible dans un libellé est le mode de panne le plus déroutant qu'on
     * puisse livrer : mieux vaut un caractère approché et un fichier lisible.
     */
    private static String assainir(String texte) {
        if (texte == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texte.length());
        for (char c : texte.toCharArray()) {
            switch (c) {
                case '\u202f', '\u00a0', '\u2009' -> sb.append(' ');
                case '\u2019', '\u2018' -> sb.append('\'');
                case '\u201c', '\u201d' -> sb.append('"');
                case '\u2026' -> sb.append("...");
                case '\u2013' -> sb.append('-');
                default -> sb.append(encodable(c) ? c : '?');
            }
        }
        return sb.toString();
    }

    /** WinAnsi couvre le latin-1 et quelques signes typographiques ; au-del\u00e0, on approche. */
    private static boolean encodable(char c) {
        return c == '\n' || c == '\r' || c == '\t'
                || (c >= 0x20 && c <= 0x7e)
                || (c >= 0xa0 && c <= 0xff)
                || c == '\u20ac' || c == '\u2014' || c == '\u00ab' || c == '\u00bb';
    }
}
