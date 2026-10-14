package mr.gov.finances.sgci.service.reporting.export;

import mr.gov.finances.sgci.service.reporting.model.ReportingDossier;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rend un état en classeur, une feuille par section.
 *
 * <p>Apache POI était déjà au projet, employé en lecture pour importer les relevés de l'ancienne
 * application. Ici il écrit, en {@code SXSSFWorkbook} : le classeur ne tient pas tout en mémoire,
 * il déborde sur disque au-delà d'une fenêtre de lignes.
 *
 * <p><b>Trois pièges de POI en écriture, chacun avec sa parade.</b>
 * <ul>
 *   <li>{@code SXSSFWorkbook} laisse des fichiers temporaires si {@code dispose()} n'est pas
 *       appelé — d'où le {@code finally}. Sur un serveur qui exporte souvent, l'oubli finit par
 *       remplir le disque.</li>
 *   <li>Les largeurs de colonnes sont <b>fixes</b>. {@code autoSizeColumn} exige de suivre toutes
 *       les colonnes en mémoire et coûte autant que le nombre de lignes : c'est reprendre d'une
 *       main ce que le mode streaming donne de l'autre.</li>
 *   <li>Le nom d'une feuille Excel est limité à 31 caractères et interdit certains signes.
 *       {@code WorkbookUtil} s'en charge, et l'unicité est garantie ici — deux feuilles de même nom
 *       font échouer l'écriture.</li>
 * </ul>
 *
 * <p>Les nombres sont écrits en <b>cellules numériques</b> quand la valeur en est un : un montant
 * laissé en texte est un montant que le lecteur ne peut pas additionner, et c'est la première chose
 * qu'il essaiera de faire.
 *
 * <p>La signature prend un flux plutôt que de rendre un tableau d'octets : c'est ce qui permet au
 * contrôleur d'écrire directement dans la réponse, et au test de relire le classeur depuis un flux
 * mémoire sans démarrer de serveur.
 */
@Component
public class XlsxReportWriter {

    private static final int FENETRE_LIGNES = 200;
    private static final int LARGEUR_DEFAUT = 18 * 256;
    private static final int LARGEUR_LARGE = 40 * 256;

    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    public void ecrire(ReportingDossier dossier, OutputStream sortie) throws IOException {
        SXSSFWorkbook classeur = new SXSSFWorkbook(FENETRE_LIGNES);
        try {
            CellStyle gras = styleGras(classeur);
            ecrireEnTete(classeur, dossier, gras);
            Set<String> nomsUtilises = new HashSet<>();
            for (ReportingDossier.Section section : dossier.sections()) {
                ecrireSection(classeur, section, gras, nomsUtilises);
            }
            classeur.write(sortie);
        } finally {
            // Sans cela, les fichiers temporaires du mode streaming restent sur le disque.
            classeur.dispose();
        }
    }

    private void ecrireEnTete(SXSSFWorkbook classeur, ReportingDossier dossier, CellStyle gras) {
        ReportingDossier.EnTete e = dossier.enTete();
        Sheet feuille = classeur.createSheet("Informations");
        feuille.setColumnWidth(0, LARGEUR_DEFAUT);
        feuille.setColumnWidth(1, LARGEUR_LARGE);
        int ligne = 0;
        ligne = paire(feuille, ligne, "\u00c9tat", e.titre(), gras);
        ligne = paire(feuille, ligne, "P\u00e9rim\u00e8tre", e.perimetre(), gras);
        ligne = paire(feuille, ligne, "P\u00e9riode", e.fenetre(), gras);
        ligne = paire(feuille, ligne, "\u00c9dit\u00e9 par", e.auteur(), gras);
        ligne = paire(feuille, ligne, "\u00c9dit\u00e9 le",
                e.genereLe() != null ? HORODATAGE.format(e.genereLe()) : "", gras);
        paire(feuille, ligne + 1, "Port\u00e9e des chiffres", e.mentionFiabilite(), gras);
    }

    private int paire(Sheet feuille, int indice, String cle, String valeur, CellStyle gras) {
        Row row = feuille.createRow(indice);
        Cell c0 = row.createCell(0);
        c0.setCellValue(cle);
        c0.setCellStyle(gras);
        row.createCell(1).setCellValue(valeur != null ? valeur : "");
        return indice + 1;
    }

    private void ecrireSection(SXSSFWorkbook classeur, ReportingDossier.Section section,
                               CellStyle gras, Set<String> nomsUtilises) {
        Sheet feuille = classeur.createSheet(nomUnique(section.titre(), nomsUtilises));
        int indice = 0;

        Row titre = feuille.createRow(indice++);
        Cell cellTitre = titre.createCell(0);
        cellTitre.setCellValue(section.titre());
        cellTitre.setCellStyle(gras);

        if (section.mentionTroncature() != null) {
            // La troncature doit figurer dans le fichier : une liste coup\u00e9e en silence se lit
            // comme une liste compl\u00e8te, et fait conclure que le travail est fait.
            feuille.createRow(indice++).createCell(0).setCellValue(section.mentionTroncature());
        }
        indice++;

        Row entetes = feuille.createRow(indice++);
        List<String> colonnes = section.colonnes();
        for (int i = 0; i < colonnes.size(); i++) {
            Cell cell = entetes.createCell(i);
            cell.setCellValue(colonnes.get(i));
            cell.setCellStyle(gras);
            feuille.setColumnWidth(i, LARGEUR_DEFAUT);
        }

        for (List<String> valeurs : section.lignes()) {
            Row row = feuille.createRow(indice++);
            for (int i = 0; i < valeurs.size(); i++) {
                ecrireValeur(row.createCell(i), valeurs.get(i));
            }
        }

        if (section.note() != null) {
            indice++;
            feuille.createRow(indice).createCell(0).setCellValue(section.note());
        }
    }

    /** Un nombre reconnaissable devient une cellule num\u00e9rique ; le reste reste du texte. */
    private void ecrireValeur(Cell cell, String valeur) {
        if (valeur == null) {
            cell.setCellValue("");
            return;
        }
        try {
            cell.setCellValue(Double.parseDouble(valeur));
        } catch (NumberFormatException e) {
            cell.setCellValue(valeur);
        }
    }

    /**
     * Un nom de feuille valide et unique.
     *
     * <p>Excel plafonne \u00e0 31 caract\u00e8res et refuse certains signes ; deux feuilles de m\u00eame nom font
     * \u00e9chouer l'\u00e9criture enti\u00e8re, et nos titres de sections se ressemblent par construction.
     */
    private String nomUnique(String titre, Set<String> deja) {
        String propre = WorkbookUtil.createSafeSheetName(titre != null ? titre : "Section");
        String candidat = propre;
        int suffixe = 2;
        while (!deja.add(candidat)) {
            String abrege = propre.length() > 28 ? propre.substring(0, 28) : propre;
            candidat = abrege + " " + suffixe++;
        }
        return candidat;
    }

    private CellStyle styleGras(SXSSFWorkbook classeur) {
        Font police = classeur.createFont();
        police.setBold(true);
        CellStyle style = classeur.createCellStyle();
        style.setFont(police);
        return style;
    }
}
