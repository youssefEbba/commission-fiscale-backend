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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *
 * <p><b>Mise en page des tableaux.</b> L'Helvetica est une police proportionnelle : aligner des
 * colonnes en complétant le texte d'espaces décale les valeurs d'une ligne à l'autre. Chaque
 * cellule est donc placée à une abscisse calculée et coupée selon sa largeur réelle ; le document
 * est en A4 paysage, les tableaux d'état ayant jusqu'à une vingtaine de colonnes. Les nombres du
 * dossier sont écrits pour le tableur (« 734746.00 ») : le PDF les remet au format français
 * (« 734 746,00 ») et les aligne à droite.
 */
@Component
public class PdfReportWriter {

    private static final PDRectangle PAYSAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGE = 36f;
    private static final float TAILLE_TITRE = 13f;
    private static final float TAILLE_TEXTE = 8.5f;
    private static final float PADDING = 3f;
    private static final float LARGEUR_MAX_COLONNE = 220f;
    private static final float LARGEUR_MIN_COLONNE = 28f;
    /** Largeur à laquelle une colonne de texte peut être réduite pour laisser entiers les montants. */
    private static final float LARGEUR_TEXTE_REDUITE = 90f;
    private static final int CARACTERES_PAR_LIGNE = 165;

    /** Nombre du dossier : point décimal, sans séparateur de milliers (cf. ReportingDossierService). */
    private static final Pattern NOMBRE_DECIMAL = Pattern.compile("(?<![\\w.,])(-?)(\\d+)\\.(\\d{1,2})(?!\\d|\\.\\d)");
    private static final Pattern CELLULE_NUMERIQUE = Pattern.compile("-?[\\d ]+(,\\d+)?( %)?");

    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    public void ecrire(ReportingDossier dossier, OutputStream sortie) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont normale = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont gras = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            Page page = new Page(document);
            ReportingDossier.EnTete e = dossier.enTete();
            page.texte(e.titre(), gras, TAILLE_TITRE);
            page.texte("Périmètre : " + e.perimetre(), normale, TAILLE_TEXTE);
            page.texte("Période : " + e.fenetre(), normale, TAILLE_TEXTE);
            page.texte("Édité par " + e.auteur()
                    + (e.genereLe() != null ? " le " + HORODATAGE.format(e.genereLe()) : ""), normale, TAILLE_TEXTE);
            page.saut(6f);
            for (String morceau : decouper(e.mentionFiabilite(), CARACTERES_PAR_LIGNE)) {
                page.texte(morceau, normale, TAILLE_TEXTE);
            }

            for (ReportingDossier.Section section : dossier.sections()) {
                page.saut(10f);
                page.garderAvecLaSuite(TAILLE_TITRE + 4 * (TAILLE_TEXTE + 4));
                page.texte(section.titre(), gras, TAILLE_TITRE);
                if (section.mentionTroncature() != null) {
                    page.texte(section.mentionTroncature(), gras, TAILLE_TEXTE);
                }
                page.tableau(section.colonnes(), section.lignes(), normale, gras);
                if (section.note() != null) {
                    page.saut(4f);
                    for (String morceau : decouper(enFrancais(section.note()), CARACTERES_PAR_LIGNE)) {
                        page.texte(morceau, normale, TAILLE_TEXTE);
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
        private float largeur;

        private Page(PDDocument document) throws IOException {
            this.document = document;
            nouvelle();
        }

        private void nouvelle() throws IOException {
            if (flux != null) {
                flux.close();
            }
            PDPage page = new PDPage(PAYSAGE);
            document.addPage(page);
            flux = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGE;
            largeur = page.getMediaBox().getWidth() - 2 * MARGE;
        }

        private void saut(float hauteur) {
            y -= hauteur;
        }

        /** Change de page si la hauteur demandée ne tient plus : un titre ne reste pas seul en bas. */
        private void garderAvecLaSuite(float hauteur) throws IOException {
            if (y - hauteur < MARGE) {
                nouvelle();
            }
        }

        private void texte(String texte, PDFont police, float taille) throws IOException {
            garderAvecLaSuite(taille + 4f);
            ecrireA(MARGE, y - taille, texte, police, taille);
            y -= taille + 4f;
        }

        private void ecrireA(float x, float base, String texte, PDFont police, float taille) throws IOException {
            flux.beginText();
            flux.setFont(police, taille);
            flux.newLineAtOffset(x, base);
            flux.showText(assainir(texte));
            flux.endText();
        }

        private void tableau(List<String> colonnes, List<List<String>> lignes, PDFont normale, PDFont gras) throws IOException {
            int n = colonnes.size();
            if (n == 0) {
                return;
            }
            float taille = n <= 8 ? 8f : n <= 12 ? 7f : 6.5f;
            List<List<String>> cellules = new ArrayList<>();
            for (List<String> l : lignes) {
                List<String> c = new ArrayList<>();
                for (String v : l) {
                    c.add(enFrancais(v != null ? v : ""));
                }
                cellules.add(c);
            }
            boolean[] chiffres = colonnesNumeriques(n, cellules);
            float[] w = largeursNaturelles(colonnes, cellules, normale, gras, taille);

            // 1. Tout tient : un seul tableau.
            if (somme(w) <= largeur) {
                dessinerTableau(colonnes, cellules, indices(0, n), w, normale, gras, taille);
                return;
            }
            // 2. Un montant ne se tronque jamais : on rétrécit d'abord les colonnes de texte.
            float fixe = 0, texte = 0;
            for (int i = 0; i < n; i++) {
                if (chiffres[i]) fixe += w[i]; else texte += w[i];
            }
            float minTexte = 0;
            for (int i = 0; i < n; i++) {
                if (!chiffres[i]) minTexte += Math.min(w[i], LARGEUR_TEXTE_REDUITE);
            }
            if (fixe + minTexte <= largeur) {
                float k = (largeur - fixe) / texte;
                for (int i = 0; i < n; i++) {
                    if (!chiffres[i]) w[i] = Math.max(Math.min(w[i], LARGEUR_TEXTE_REDUITE), w[i] * k);
                }
                dessinerTableau(colonnes, cellules, indices(0, n), w, normale, gras, taille);
                return;
            }
            // 3. Trop de colonnes de montants : le tableau se lit en plusieurs parties, chacune
            //    répétant les colonnes qui identifient la ligne (les deux premières).
            int cles = Math.min(2, n);
            float largeurCles = 0;
            for (int i = 0; i < cles; i++) {
                if (!chiffres[i]) w[i] = Math.min(w[i], LARGEUR_TEXTE_REDUITE);
                largeurCles += w[i];
            }
            List<List<Integer>> parties = new ArrayList<>();
            List<Integer> courante = new ArrayList<>();
            float occupe = largeurCles;
            for (int i = cles; i < n; i++) {
                if (!courante.isEmpty() && occupe + w[i] > largeur) {
                    parties.add(courante);
                    courante = new ArrayList<>();
                    occupe = largeurCles;
                }
                courante.add(i);
                occupe += w[i];
            }
            if (!courante.isEmpty()) {
                parties.add(courante);
            }
            for (int p = 0; p < parties.size(); p++) {
                if (p > 0) {
                    saut(6f);
                    texte("(suite, " + (p + 1) + "/" + parties.size() + ")", gras, TAILLE_TEXTE);
                }
                List<Integer> cols = new ArrayList<>(indices(0, cles));
                cols.addAll(parties.get(p));
                dessinerTableau(colonnes, cellules, cols, w, normale, gras, taille);
            }
        }

        private void dessinerTableau(List<String> colonnes, List<List<String>> cellules, List<Integer> cols, float[] w,
                                     PDFont normale, PDFont gras, float taille) throws IOException {
            float[] largeurs = new float[cols.size()];
            List<List<String>> entete = new ArrayList<>();
            int lignesEntete = 1;
            for (int j = 0; j < cols.size(); j++) {
                largeurs[j] = w[cols.get(j)];
                List<String> morceaux = couper(colonnes.get(cols.get(j)), gras, taille, largeurs[j] - 2 * PADDING, 2);
                entete.add(morceaux);
                lignesEntete = Math.max(lignesEntete, morceaux.size());
            }
            float hLigne = taille + 4f;
            float hEntete = lignesEntete * hLigne + 2f;

            garderAvecLaSuite(hEntete + hLigne);
            dessinerEntete(entete, largeurs, gras, taille, hLigne, hEntete);
            if (cellules.isEmpty()) {
                texte("Aucune ligne.", normale, TAILLE_TEXTE);
                return;
            }
            for (List<String> ligne : cellules) {
                if (y - hLigne < MARGE) {
                    nouvelle();
                    dessinerEntete(entete, largeurs, gras, taille, hLigne, hEntete);
                }
                float x = MARGE;
                for (int j = 0; j < cols.size(); j++) {
                    int i = cols.get(j);
                    String v = i < ligne.size() ? ligne.get(i) : "";
                    String coupe = tronquer(v, normale, taille, largeurs[j] - 2 * PADDING);
                    float tx = numerique(v)
                            ? x + largeurs[j] - PADDING - largeurTexte(coupe, normale, taille)
                            : x + PADDING;
                    ecrireA(tx, y - taille, coupe, normale, taille);
                    x += largeurs[j];
                }
                y -= hLigne;
            }
        }

        private void dessinerEntete(List<List<String>> entete, float[] largeurs, PDFont gras, float taille,
                                    float hLigne, float hEntete) throws IOException {
            float x = MARGE;
            for (int i = 0; i < entete.size(); i++) {
                float yy = y;
                for (String morceau : entete.get(i)) {
                    ecrireA(x + PADDING, yy - taille, morceau, gras, taille);
                    yy -= hLigne;
                }
                x += largeurs[i];
            }
            y -= hEntete;
            flux.setLineWidth(0.5f);
            flux.moveTo(MARGE, y + 2f);
            flux.lineTo(MARGE + somme(largeurs), y + 2f);
            flux.stroke();
        }

        /** Largeur naturelle de chaque colonne (en-tête sur deux lignes au plus, contenu le plus large). */
        private float[] largeursNaturelles(List<String> colonnes, List<List<String>> cellules, PDFont normale, PDFont gras,
                                 float taille) throws IOException {
            int n = colonnes.size();
            float[] w = new float[n];
            for (int i = 0; i < n; i++) {
                // L'en-tête peut passer sur deux lignes : on compte son mot le plus long, pas tout le libellé.
                float m = 0;
                for (String mot : colonnes.get(i).split(" ")) {
                    m = Math.max(m, largeurTexte(mot, gras, taille));
                }
                m = Math.max(m, largeurTexte(colonnes.get(i), gras, taille) / 2f);
                for (List<String> l : cellules) {
                    if (i < l.size()) {
                        m = Math.max(m, largeurTexte(l.get(i), normale, taille));
                    }
                }
                w[i] = Math.max(LARGEUR_MIN_COLONNE, Math.min(LARGEUR_MAX_COLONNE, m + 2 * PADDING));
            }
            return w;
        }

        private void fermer() throws IOException {
            if (flux != null) {
                flux.close();
                flux = null;
            }
        }
    }

    /** Colonne de nombres : toutes ses valeurs renseignées sont numériques (« n/a » mis à part). */
    private static boolean[] colonnesNumeriques(int n, List<List<String>> cellules) {
        boolean[] out = new boolean[n];
        for (int i = 0; i < n; i++) {
            boolean auMoinsUn = false, tous = true;
            for (List<String> l : cellules) {
                String v = i < l.size() ? l.get(i) : "";
                if (v == null || v.isBlank() || v.equals("n/a")) continue;
                auMoinsUn = true;
                if (!numerique(v)) { tous = false; break; }
            }
            out[i] = auMoinsUn && tous;
        }
        return out;
    }

    private static List<Integer> indices(int debut, int fin) {
        List<Integer> out = new ArrayList<>();
        for (int i = debut; i < fin; i++) out.add(i);
        return out;
    }

    private static float somme(float[] w) {
        float s = 0;
        for (float v : w) {
            s += v;
        }
        return s;
    }

    private static float largeurTexte(String texte, PDFont police, float taille) throws IOException {
        return police.getStringWidth(assainir(texte)) / 1000f * taille;
    }

    /** Coupe le texte à la largeur disponible, avec « ... » s'il dépasse. */
    private static String tronquer(String texte, PDFont police, float taille, float max) throws IOException {
        if (largeurTexte(texte, police, taille) <= max) {
            return texte;
        }
        String t = texte;
        while (!t.isEmpty() && largeurTexte(t + "...", police, taille) > max) {
            t = t.substring(0, t.length() - 1);
        }
        return t.isEmpty() ? "" : t.stripTrailing() + "...";
    }

    /** Répartit un libellé sur au plus {@code maxLignes} lignes ; la dernière est tronquée si besoin. */
    private static List<String> couper(String texte, PDFont police, float taille, float max, int maxLignes) throws IOException {
        List<String> out = new ArrayList<>();
        StringBuilder courante = new StringBuilder();
        String[] mots = texte.split(" ");
        for (int i = 0; i < mots.length; i++) {
            String essai = courante.length() == 0 ? mots[i] : courante + " " + mots[i];
            if (largeurTexte(essai, police, taille) <= max || courante.length() == 0) {
                courante.setLength(0);
                courante.append(essai);
            } else {
                out.add(courante.toString());
                if (out.size() == maxLignes - 1) {
                    StringBuilder reste = new StringBuilder(mots[i]);
                    for (int j = i + 1; j < mots.length; j++) {
                        reste.append(' ').append(mots[j]);
                    }
                    out.add(tronquer(reste.toString(), police, taille, max));
                    return out;
                }
                courante.setLength(0);
                courante.append(mots[i]);
            }
        }
        if (courante.length() > 0) {
            out.add(tronquer(courante.toString(), police, taille, max));
        }
        return out;
    }

    /** « 734746.00 » → « 734 746,00 » ; les autres textes sont rendus tels quels. */
    static String enFrancais(String texte) {
        if (texte == null || texte.isEmpty()) {
            return texte;
        }
        Matcher m = NOMBRE_DECIMAL.matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String entier = m.group(2);
            StringBuilder groupe = new StringBuilder();
            for (int i = 0; i < entier.length(); i++) {
                if (i > 0 && (entier.length() - i) % 3 == 0) {
                    groupe.append(' ');
                }
                groupe.append(entier.charAt(i));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + groupe + "," + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean numerique(String v) {
        return v != null && !v.isBlank() && CELLULE_NUMERIQUE.matcher(v.trim()).matches();
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
                case ' ', ' ', ' ' -> sb.append(' ');
                case '’', '‘' -> sb.append('\'');
                case '“', '”' -> sb.append('"');
                case '…' -> sb.append("...");
                case '–' -> sb.append('-');
                default -> sb.append(encodable(c) ? c : '?');
            }
        }
        return sb.toString();
    }

    /** WinAnsi couvre le latin-1 et quelques signes typographiques ; au-delà, on approche. */
    private static boolean encodable(char c) {
        return c == '\n' || c == '\r' || c == '\t'
                || (c >= 0x20 && c <= 0x7e)
                || (c >= 0xa0 && c <= 0xff)
                || c == '€' || c == '—' || c == '«' || c == '»';
    }
}
