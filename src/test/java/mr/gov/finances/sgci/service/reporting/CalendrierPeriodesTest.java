package mr.gov.finances.sgci.service.reporting;

import mr.gov.finances.sgci.domain.enums.Granularite;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le découpage du temps, vérifié sans base ni contexte Spring.
 *
 * <p>C'est tout l'intérêt d'avoir sorti ce calcul des requêtes : les trois pièges qu'il désamorce
 * — mois vide omis, trimestre calculé par la base, variation contre une période vide — se testent
 * ici en quelques millisecondes, alors qu'ils ne se verraient autrement qu'à l'écran, et seulement
 * si quelqu'un recoupait à la main.
 *
 * <p>Nommé {@code *Test} et sans Spring : il s'exécute à chaque build, pas seulement sur demande
 * comme les {@code *IT} de ce projet.
 */
class CalendrierPeriodesTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    // ── le comblement ──────────────────────────────────────────────────────────────────────────

    @Test
    void unMoisSansDonneeEstComblePasOmis() {
        // Sans comblement, la courbe relie janvier à mars en ligne droite et l'arrêt de février
        // disparaît. C'est précisément l'information qu'on cherchait.
        Map<String, Long> parMois = new LinkedHashMap<>();
        parMois.put("2026-01", 5L);
        parMois.put("2026-03", 7L);

        Map<String, Long> comble = CalendrierPeriodes.replier(
                parMois, instant(2026, 1, 1), instant(2026, 3, 31), Granularite.MOIS);

        assertThat(comble).containsExactly(
                org.assertj.core.data.MapEntry.entry("2026-01", 5L),
                org.assertj.core.data.MapEntry.entry("2026-02", 0L),
                org.assertj.core.data.MapEntry.entry("2026-03", 7L));
    }

    @Test
    void toutesLesPeriodesDeLaFenetreSontPresentes() {
        assertThat(CalendrierPeriodes.periodes(
                instant(2026, 1, 15), instant(2026, 4, 2), Granularite.MOIS))
                .containsExactly("2026-01", "2026-02", "2026-03", "2026-04");
    }

    // ── le trimestre ───────────────────────────────────────────────────────────────────────────

    @Test
    void leTrimestreEstLaSommeDeSesTroisMois() {
        // Replié en Java, jamais demandé à la base : le rendu de extract(quarter) diverge entre H2
        // et MySQL, et un trimestre décalé en production seulement ne se verrait jamais en test.
        Map<String, Long> parMois = new LinkedHashMap<>();
        parMois.put("2026-07", 3L);
        parMois.put("2026-08", 4L);
        parMois.put("2026-09", 5L);

        Map<String, Long> replie = CalendrierPeriodes.replier(
                parMois, instant(2026, 7, 1), instant(2026, 9, 30), Granularite.TRIMESTRE);

        assertThat(replie).containsExactly(
                org.assertj.core.data.MapEntry.entry("2026-T3", 12L));
    }

    @Test
    void lesBornesDesTrimestresSontJustes() {
        assertThat(CalendrierPeriodes.cleTrimestre(2026, 1)).isEqualTo("2026-T1");
        assertThat(CalendrierPeriodes.cleTrimestre(2026, 3)).isEqualTo("2026-T1");
        assertThat(CalendrierPeriodes.cleTrimestre(2026, 4)).isEqualTo("2026-T2");
        assertThat(CalendrierPeriodes.cleTrimestre(2026, 12)).isEqualTo("2026-T4");
    }

    @Test
    void unTrimestreVideEstPresentAZero() {
        Map<String, Long> parMois = new LinkedHashMap<>();
        parMois.put("2026-01", 2L);

        Map<String, Long> replie = CalendrierPeriodes.replier(
                parMois, instant(2026, 1, 1), instant(2026, 6, 30), Granularite.TRIMESTRE);

        assertThat(replie).containsExactly(
                org.assertj.core.data.MapEntry.entry("2026-T1", 2L),
                org.assertj.core.data.MapEntry.entry("2026-T2", 0L));
    }

    // ── la variation ───────────────────────────────────────────────────────────────────────────

    @Test
    void uneVariationContreUnePeriodeVideEstNullePasMoinsCent() {
        // Comparer à une période antérieure au début des données donnerait -100 %, qui se lit
        // « effondrement » alors qu'il signifie « pas de référence ».
        assertThat(CalendrierPeriodes.variation(0L, 0L)).isNull();
        assertThat(CalendrierPeriodes.variation(12L, 0L)).isNull();
        assertThat(CalendrierPeriodes.variation(BigDecimal.TEN, BigDecimal.ZERO)).isNull();
        assertThat(CalendrierPeriodes.variation(BigDecimal.TEN, null)).isNull();
    }

    @Test
    void uneVraieBaisseEstBienRendue() {
        // Et l'inverse doit marcher : un vrai effondrement doit s'afficher comme tel.
        assertThat(CalendrierPeriodes.variation(0L, 10L)).isEqualTo(-100.0);
        assertThat(CalendrierPeriodes.variation(15L, 10L)).isEqualTo(50.0);
        assertThat(CalendrierPeriodes.variation(10L, 10L)).isZero();
    }

    // ── la fenêtre précédente ──────────────────────────────────────────────────────────────────

    @Test
    void laFenetrePrecedenteALaMemeDureeEtNeChevauchePas() {
        Instant debut = instant(2026, 4, 1);
        Instant fin = instant(2026, 6, 30);

        Instant[] precedente = CalendrierPeriodes.fenetrePrecedente(debut, fin);

        assertThat(precedente[1]).isBefore(debut);
        long dureeCourante = java.time.Duration.between(debut, fin).toSeconds();
        long dureePrecedente = java.time.Duration.between(precedente[0], precedente[1]).toSeconds();
        assertThat(dureePrecedente).isEqualTo(dureeCourante);
    }

    // ── utilitaire ─────────────────────────────────────────────────────────────────────────────

    private static Instant instant(int annee, int mois, int jour) {
        return LocalDate.of(annee, mois, jour).atStartOfDay(ZONE).toInstant();
    }
}
