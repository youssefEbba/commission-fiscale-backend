package mr.gov.finances.sgci.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Montants écrits en toutes lettres par le serveur, au format des écrans : deux décimales, virgule
 * décimale, milliers séparés par une espace insécable. « 495277.8900 » donne « 495 277,89 MRU »
 * (A48 : la file de clôture affichait le {@code BigDecimal} brut).
 */
public final class MontantsTexte {

    private MontantsTexte() {
    }

    public static String montant(BigDecimal valeur) {
        if (valeur == null) {
            return "—";
        }
        DecimalFormatSymbols symboles = new DecimalFormatSymbols();
        symboles.setDecimalSeparator(',');
        symboles.setGroupingSeparator(' ');
        symboles.setMinusSign('-');
        DecimalFormat format = new DecimalFormat("#,##0.00", symboles);
        return format.format(valeur.setScale(2, RoundingMode.HALF_UP));
    }

    public static String mru(BigDecimal valeur) {
        return montant(valeur) + " MRU";
    }
}
