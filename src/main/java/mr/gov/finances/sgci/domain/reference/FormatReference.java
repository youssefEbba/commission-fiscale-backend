package mr.gov.finances.sgci.domain.reference;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le format des références lisibles : {@code PREFIXE-NNNN-MM-AAAA}, ou {@code PREFIXE-NNNN-AAAA}
 * sans mois (certificat d'utilisation).
 *
 * <p>Demandé par l'équipe en octobre 2026 : quatre chiffres, et des tirets au lieu de la barre
 * oblique, qui gênait dans les adresses et les noms de fichiers. L'ancien format
 * ({@code DC-109-10/2026}, {@code CU-007/2026}) reste reconnu : les références déjà attribuées sont
 * converties au démarrage, et un code lu sur un papier ou un QR code imprimé avant doit encore
 * retrouver son dossier.
 */
public final class FormatReference {

    private static final Pattern ANCIEN_AVEC_MOIS = Pattern.compile("^([A-Z]{2,4})-(\\d{1,6})-(\\d{2})/(\\d{4})$");
    private static final Pattern ANCIEN_SANS_MOIS = Pattern.compile("^([A-Z]{2,4})-(\\d{1,6})/(\\d{4})$");

    private FormatReference() {
    }

    public static String avecMois(String prefixe, long numero, int mois, int annee) {
        return String.format("%s-%04d-%02d-%d", prefixe, numero, mois, annee);
    }

    public static String sansMois(String prefixe, long numero, int annee) {
        return String.format("%s-%04d-%d", prefixe, numero, annee);
    }

    /**
     * La même référence au format actuel si elle est à l'ancien, sinon la valeur inchangée.
     *
     * <p>Espaces et casse sont tolérés, comme à la saisie. Le numéro garde sa valeur : seul son
     * affichage passe à quatre chiffres.
     */
    public static String convertirAncienFormat(String reference) {
        if (reference == null) {
            return null;
        }
        String r = reference.trim().toUpperCase(Locale.ROOT);
        Matcher m = ANCIEN_AVEC_MOIS.matcher(r);
        if (m.matches()) {
            return avecMois(m.group(1), Long.parseLong(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        }
        m = ANCIEN_SANS_MOIS.matcher(r);
        if (m.matches()) {
            return sansMois(m.group(1), Long.parseLong(m.group(2)), Integer.parseInt(m.group(3)));
        }
        return reference;
    }

    /** Vrai si la valeur est à l'ancien format, donc à convertir. */
    public static boolean estAncienFormat(String reference) {
        if (reference == null) {
            return false;
        }
        String r = reference.trim().toUpperCase(Locale.ROOT);
        return ANCIEN_AVEC_MOIS.matcher(r).matches() || ANCIEN_SANS_MOIS.matcher(r).matches();
    }
}
