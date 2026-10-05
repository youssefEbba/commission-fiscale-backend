package mr.gov.finances.sgci.config;

import jakarta.persistence.Column;
import mr.gov.finances.sgci.config.ColonnesTexteSchemaMigration.ColonneTexte;
import mr.gov.finances.sgci.config.ColonnesTexteSchemaMigration.ColumnInfo;
import mr.gov.finances.sgci.domain.entity.Notification;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une colonne texte doit pouvoir accueillir toutes les valeurs de son énumération.
 *
 * <p>C'est le seul garde-fou possible contre « Data truncated for column … » : les tests tournent
 * sur H2, schéma recréé à chaque exécution, et ne verront jamais la colonne MySQL réelle. Ce que
 * l'on peut verrouiller ici, c'est que la longueur inscrite au registre soit celle que l'entité
 * déclare, et qu'aucune valeur d'énumération ne la dépasse — le jour où quelqu'un ajoute un statut
 * de quarante caractères, le build rougit au lieu de la production.
 *
 * <p>Nommé {@code *Test} et sans Spring : il doit s'exécuter à chaque build, pas seulement sur
 * demande comme les {@code *IT} de ce projet.
 */
class ColonnesTexteSchemaMigrationTest {

    /** Où vit, dans le modèle, la colonne inscrite au registre. */
    private static final Map<String, FieldRef> ORIGINE = Map.of(
            "notification.type", new FieldRef(Notification.class, "type"),
            "notification.entity_type", new FieldRef(Notification.class, "entityType"),
            "utilisation_credit.statut", new FieldRef(UtilisationCredit.class, "statut"));

    private record FieldRef(Class<?> entite, String champ) {
    }

    // ── la cohérence du registre ───────────────────────────────────────────────────────────────

    @Test
    void chaqueColonneDuRegistreEstRattacheeAUnChamp() {
        // Sans cette assertion, on peut ajouter une ligne au registre sans que rien ne vérifie
        // qu'elle tient ses promesses.
        for (ColonneTexte c : ColonnesTexteSchemaMigration.REGISTRE) {
            assertThat(ORIGINE)
                    .as("Colonne %s.%s inscrite au registre mais non rattachée à un champ d'entité",
                            c.table(), c.colonne())
                    .containsKey(c.table() + "." + c.colonne());
        }
    }

    @Test
    void laLongueurDuRegistreEstCelleDeclareeParLentite() throws Exception {
        for (ColonneTexte c : ColonnesTexteSchemaMigration.REGISTRE) {
            FieldRef ref = ORIGINE.get(c.table() + "." + c.colonne());
            Field field = ref.entite().getDeclaredField(ref.champ());
            Column column = field.getAnnotation(Column.class);

            assertThat(column)
                    .as("%s#%s n'est pas annoté @Column", ref.entite().getSimpleName(), ref.champ())
                    .isNotNull();
            assertThat(column.length())
                    .as("Le registre élargit %s.%s à %d alors que l'entité en déclare %d : la base "
                            + "et le modèle divergeraient", c.table(), c.colonne(), c.longueur(),
                            column.length())
                    .isEqualTo(c.longueur());
        }
    }

    @Test
    void aucuneValeurDenumerationNeDepasseLaLongueurDeSaColonne() throws Exception {
        for (ColonneTexte c : ColonnesTexteSchemaMigration.REGISTRE) {
            FieldRef ref = ORIGINE.get(c.table() + "." + c.colonne());
            Field field = ref.entite().getDeclaredField(ref.champ());
            if (!field.getType().isEnum()) {
                continue;
            }
            for (Object valeur : field.getType().getEnumConstants()) {
                String nom = ((Enum<?>) valeur).name();
                assertThat(nom.length())
                        .as("La valeur %s.%s fait %d caractères, la colonne %s.%s n'en accepte que %d",
                                field.getType().getSimpleName(), nom, nom.length(),
                                c.table(), c.colonne(), c.longueur())
                        .isLessThanOrEqualTo(c.longueur());
            }
        }
    }

    @Test
    void laColonneDuBugEstBienInscrite() {
        // Le défaut rapporté en recette : TRANSMISE_AU_PRESIDENT refusé par un ENUM antérieur.
        assertThat(ColonnesTexteSchemaMigration.REGISTRE)
                .extracting(ColonneTexte::table, ColonneTexte::colonne)
                .contains(org.assertj.core.groups.Tuple.tuple("utilisation_credit", "statut"));
    }

    // ── la décision d'élargir ──────────────────────────────────────────────────────────────────

    @Test
    void unEnumEstToujoursElargi() {
        // Quelle que soit sa liste de valeurs : on ne sait pas la comparer, et une colonne ENUM
        // ne gagnera jamais de valeur par ddl-auto.
        assertThat(new ColumnInfo("enum", null).needsWiden(32)).isTrue();
        assertThat(new ColumnInfo("ENUM", 25).needsWiden(32)).isTrue();
    }

    @Test
    void unVarcharTropCourtEstElargi() {
        assertThat(new ColumnInfo("varchar", 20).needsWiden(32)).isTrue();
        assertThat(new ColumnInfo("char", 10).needsWiden(32)).isTrue();
        assertThat(new ColumnInfo("varchar", null).needsWiden(32)).isTrue();
    }

    @Test
    void unVarcharDejaConformeNestPasTouche() {
        // L'idempotence tient à cette ligne : sans elle, un ALTER serait émis à chaque démarrage.
        assertThat(new ColumnInfo("varchar", 32).needsWiden(32)).isFalse();
        assertThat(new ColumnInfo("varchar", 64).needsWiden(32)).isFalse();
    }

    @Test
    void unTypeNonTextuelEstLaisseIntact() {
        for (String type : List.of("bigint", "datetime", "decimal", "text", "tinyint")) {
            assertThat(new ColumnInfo(type, null).needsWiden(32))
                    .as("type %s", type).isFalse();
        }
        assertThat(new ColumnInfo(null, null).needsWiden(32)).isFalse();
    }
}
