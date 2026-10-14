package mr.gov.finances.sgci.service.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Le cadrage d'une interrogation de reporting : la fenêtre temporelle et le périmètre du rôle.
 *
 * <p>Était un {@code record} privé de {@code ReportingService}. Il devient public parce que les
 * quatre blocs de contenu doivent partager exactement le même cadrage : un périmètre recalculé
 * différemment d'un bloc à l'autre est la façon la plus sûre de faire fuir des chiffres.
 *
 * @param perimetreVide périmètre fermé — aucune ligne n'est attendue, et aucune ne doit sortir.
 *                      Distinct d'un périmètre sans filtre : voir {@link ReportingScopeFactory}.
 */
public record ReportingScope(Instant from, Instant to,
                             Long autoriteContractanteId, Long entrepriseId, Long delegueUserId,
                             boolean nationalView, boolean filtersApplied,
                             boolean includeNationalAudit, boolean perimetreVide) {

    /**
     * La borne basse en date civile, pour les colonnes qui ne sont pas des {@code Instant}.
     *
     * <p>{@code Convention.dateFin} et {@code dateSignature} sont des {@code LocalDate} alors que
     * toute la fenêtre de reporting est en {@code Instant}. Lier l'un contre l'autre ne compare pas
     * ce que l'on croit, et renvoie silencieusement les mauvaises lignes.
     */
    public LocalDate fromDate(ZoneId zone) {
        return LocalDate.ofInstant(from, zone);
    }

    /** La borne haute en date civile. Voir {@link #fromDate(ZoneId)}. */
    public LocalDate toDate(ZoneId zone) {
        return LocalDate.ofInstant(to, zone);
    }
}
