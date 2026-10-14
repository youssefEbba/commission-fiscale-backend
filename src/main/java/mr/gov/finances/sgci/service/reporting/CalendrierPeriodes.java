package mr.gov.finances.sgci.service.reporting;

import mr.gov.finances.sgci.domain.enums.Granularite;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le découpage du temps pour la production par période, sans aucun accès à la base.
 *
 * <p><b>Pourquoi les trimestres se replient en Java.</b> {@code extract(quarter from …)} existe en
 * HQL mais son rendu diverge entre H2 — où tournent les tests — et MySQL, où tourne la production.
 * Un trimestre juste en test et décalé en exploitation est exactement le genre de défaut que
 * personne ne cherche. Le serveur n'interroge donc la base qu'en mois civils, et le trimestre est la
 * somme de ses trois mois, calculée ici.
 *
 * <p><b>Pourquoi les mois vides sont comblés.</b> Une série qui saute les mois sans dossier dessine
 * une courbe continue là où il y a eu un arrêt : le creux disparaît, et avec lui l'information. Un
 * mois sans activité vaut zéro, et zéro doit se voir.
 *
 * <p><b>Pourquoi une variation peut être nulle.</b> Comparer à une période antérieure au début des
 * données produit un {@code −100 %} qui veut dire « pas de données » et se lit « effondrement ».
 * {@link #variation} rend {@code null} dans ce cas, et c'est à l'écran d'écrire « n/a ».
 *
 * <p>Sans état et sans dépendance : c'est ce qui le rend vérifiable par un test unitaire pur, sans
 * contexte Spring ni base de données.
 */
public final class CalendrierPeriodes {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private CalendrierPeriodes() {
    }

    /** La clé d'un mois civil : {@code 2026-07}. */
    public static String cleMois(int annee, int mois) {
        return String.format("%04d-%02d", annee, mois);
    }

    /** La clé du trimestre qui contient ce mois : {@code 2026-T3}. */
    public static String cleTrimestre(int annee, int mois) {
        return String.format("%04d-T%d", annee, (mois - 1) / 3 + 1);
    }

    public static String cle(int annee, int mois, Granularite granularite) {
        return granularite == Granularite.TRIMESTRE ? cleTrimestre(annee, mois) : cleMois(annee, mois);
    }

    /**
     * Toutes les périodes de la fenêtre, dans l'ordre, y compris celles sans aucune donnée.
     *
     * <p>C'est cette liste qui comble les creux : la série est construite à partir d'elle, et non à
     * partir des lignes que la base a renvoyées.
     */
    public static List<String> periodes(Instant from, Instant to, Granularite granularite) {
        List<String> out = new ArrayList<>();
        if (from == null || to == null || from.isAfter(to)) {
            return out;
        }
        YearMonth debut = YearMonth.from(from.atZone(ZONE));
        YearMonth fin = YearMonth.from(to.atZone(ZONE));
        YearMonth courant = debut;
        while (!courant.isAfter(fin)) {
            String cle = cle(courant.getYear(), courant.getMonthValue(), granularite);
            if (out.isEmpty() || !out.get(out.size() - 1).equals(cle)) {
                out.add(cle);
            }
            courant = courant.plusMonths(1);
        }
        return out;
    }

    /**
     * Replie des comptages mensuels sur la granularité demandée, en comblant les trous.
     *
     * @param parMois comptages indexés par clé de mois, tels que la base les a rendus
     */
    public static Map<String, Long> replier(Map<String, Long> parMois, Instant from, Instant to,
                                            Granularite granularite) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String periode : periodes(from, to, granularite)) {
            out.put(periode, 0L);
        }
        parMois.forEach((cleDuMois, valeur) -> {
            String cible = cibleDe(cleDuMois, granularite);
            if (cible != null && out.containsKey(cible)) {
                out.merge(cible, valeur, Long::sum);
            }
        });
        return out;
    }

    /** Variante décimale, pour les masses financières. */
    public static Map<String, java.math.BigDecimal> replierMontants(
            Map<String, java.math.BigDecimal> parMois, Instant from, Instant to,
            Granularite granularite) {
        Map<String, java.math.BigDecimal> out = new LinkedHashMap<>();
        for (String periode : periodes(from, to, granularite)) {
            out.put(periode, java.math.BigDecimal.ZERO);
        }
        parMois.forEach((cleDuMois, valeur) -> {
            String cible = cibleDe(cleDuMois, granularite);
            if (cible != null && out.containsKey(cible)) {
                out.merge(cible, valeur, java.math.BigDecimal::add);
            }
        });
        return out;
    }

    private static String cibleDe(String cleDuMois, Granularite granularite) {
        if (cleDuMois == null || cleDuMois.length() < 7) {
            return null;
        }
        try {
            int annee = Integer.parseInt(cleDuMois.substring(0, 4));
            int mois = Integer.parseInt(cleDuMois.substring(5, 7));
            return cle(annee, mois, granularite);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * La fenêtre de même durée qui précède immédiatement celle-ci.
     *
     * @return {@code [from, to]} de la période antérieure
     */
    public static Instant[] fenetrePrecedente(Instant from, Instant to) {
        long duree = ChronoUnit.SECONDS.between(from, to);
        Instant finPrecedente = from.minusSeconds(1);
        return new Instant[] {finPrecedente.minusSeconds(duree), finPrecedente};
    }

    /**
     * La variation en pourcentage, ou {@code null} quand elle ne veut rien dire.
     *
     * <p>Une période précédente à zéro ne donne pas {@code −100 %} ni {@code +∞} : elle ne donne
     * rien, parce qu'il n'y a pas de référence. L'écran écrit « n/a », jamais « 0 % ».
     */
    public static Double variation(long courant, long precedent) {
        if (precedent == 0) {
            return null;
        }
        return Math.round(10000.0 * (courant - precedent) / precedent) / 100.0;
    }

    /** Variante décimale. Même règle : pas de référence, pas de variation. */
    public static Double variation(java.math.BigDecimal courant, java.math.BigDecimal precedent) {
        if (precedent == null || precedent.signum() == 0) {
            return null;
        }
        java.math.BigDecimal c = courant != null ? courant : java.math.BigDecimal.ZERO;
        return Math.round(10000.0 * c.subtract(precedent).doubleValue() / precedent.doubleValue()) / 100.0;
    }
}
