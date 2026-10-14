package mr.gov.finances.sgci.service.reporting.export;

import mr.gov.finances.sgci.service.reporting.model.ReportingDossier;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Rend un état en CSV, en format long.
 *
 * <p>Un CSV n'a pas d'onglets : poser plusieurs tableaux de largeurs différentes dans un seul
 * fichier plat donnerait des lignes dont les colonnes ne veulent pas dire la même chose selon
 * l'endroit — illisible par un tableur comme par un script. D'où le format long, une observation
 * par ligne, avec la section et la colonne en clair :
 *
 * <pre>
 * section;ligne;colonne;valeur
 * </pre>
 *
 * <p>Le séparateur est le point-virgule, comme l'attend un tableur en configuration française, et
 * les valeurs décimales gardent le point décimal pour rester lisibles par un script. Le fichier
 * commence par une marque d'ordre d'octets UTF-8 : sans elle, Excel sous Windows lit les accents
 * comme des caractères aberrants, et c'est le premier reproche qu'on nous ferait.
 */
@Component
public class CsvReportWriter {

    private static final char SEPARATEUR = ';';
    private static final String BOM = "\ufeff";

    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    public void ecrire(ReportingDossier dossier, OutputStream sortie) throws IOException {
        BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(sortie, StandardCharsets.UTF_8));
        out.write(BOM);
        ligne(out, "section", "ligne", "colonne", "valeur");

        ReportingDossier.EnTete e = dossier.enTete();
        ligne(out, "Informations", "0", "\u00c9tat", e.titre());
        ligne(out, "Informations", "0", "P\u00e9rim\u00e8tre", e.perimetre());
        ligne(out, "Informations", "0", "P\u00e9riode", e.fenetre());
        ligne(out, "Informations", "0", "\u00c9dit\u00e9 par", e.auteur());
        ligne(out, "Informations", "0", "\u00c9dit\u00e9 le",
                e.genereLe() != null ? HORODATAGE.format(e.genereLe()) : "");
        ligne(out, "Informations", "0", "Port\u00e9e des chiffres", e.mentionFiabilite());

        for (ReportingDossier.Section section : dossier.sections()) {
            if (section.mentionTroncature() != null) {
                ligne(out, section.titre(), "0", "Troncature", section.mentionTroncature());
            }
            if (section.note() != null) {
                ligne(out, section.titre(), "0", "Note", section.note());
            }
            List<String> colonnes = section.colonnes();
            int numero = 1;
            for (List<String> valeurs : section.lignes()) {
                for (int i = 0; i < valeurs.size() && i < colonnes.size(); i++) {
                    ligne(out, section.titre(), String.valueOf(numero),
                            colonnes.get(i), valeurs.get(i));
                }
                numero++;
            }
        }
        out.flush();
    }

    private void ligne(BufferedWriter out, String... champs) throws IOException {
        for (int i = 0; i < champs.length; i++) {
            if (i > 0) {
                out.write(SEPARATEUR);
            }
            out.write(echapper(champs[i]));
        }
        out.write("\r\n");
    }

    /** Guillemets doubl\u00e9s et champ encadr\u00e9 d\u00e8s qu'il porte un s\u00e9parateur, un guillemet ou un saut. */
    private String echapper(String valeur) {
        if (valeur == null) {
            return "";
        }
        boolean aEncadrer = valeur.indexOf(SEPARATEUR) >= 0 || valeur.indexOf('"') >= 0
                || valeur.indexOf('\n') >= 0 || valeur.indexOf('\r') >= 0;
        if (!aEncadrer) {
            return valeur;
        }
        return '"' + valeur.replace("\"", "\"\"") + '"';
    }
}
