package mr.gov.finances.sgci.config;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Garde des noms de tables, de colonnes, d'index et de contraintes que les migrations de démarrage
 * placent dans leurs requêtes.
 *
 * <p>Un identifiant SQL ne se passe pas en paramètre : il est concaténé. Dans ces migrations, tous
 * viennent de listes écrites dans le code ou du catalogue de la base, jamais d'une saisie. Cette
 * garde le rend sûr par construction, et non plus seulement par lecture : un nom qui n'est pas un
 * identifiant simple ne parvient jamais à la requête.
 */
public final class IdentifiantSql {

    private static final Pattern SIMPLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private IdentifiantSql() {
    }

    public static String sur(String nom) {
        if (nom == null || !SIMPLE.matcher(nom).matches()) {
            throw new IllegalArgumentException("Identifiant SQL refusé : " + nom);
        }
        return nom;
    }

    public static void dropColumn(JdbcTemplate jdbc, String table, String column) {
        jdbc.execute("ALTER TABLE " + sur(table) + " DROP COLUMN " + sur(column)); // NOSONAR java:S2077
    }

    public static void dropIndex(JdbcTemplate jdbc, String table, String indexName) {
        jdbc.execute("ALTER TABLE " + sur(table) + " DROP INDEX " + sur(indexName)); // NOSONAR java:S2077
    }

    public static void dropForeignKey(JdbcTemplate jdbc, String table, String constraint) {
        jdbc.execute("ALTER TABLE " + sur(table) + " DROP FOREIGN KEY " + sur(constraint)); // NOSONAR java:S2077
    }

    public static void modifyVarchar(JdbcTemplate jdbc, String table, String column, int length, boolean notNull) {
        jdbc.execute("ALTER TABLE " + sur(table) + " MODIFY COLUMN " + sur(column)
                + " VARCHAR(" + length + ")" + (notNull ? " NOT NULL" : " NULL")); // NOSONAR java:S2077
    }

    public static int copyColumnIfEmpty(JdbcTemplate jdbc, String table, String cible, String source) {
        String t = sur(table);
        String c = sur(cible);
        String s = sur(source);
        return jdbc.update("UPDATE " + t + " SET " + c + " = " + s
                + " WHERE (" + c + " IS NULL OR " + c + " = '') AND " + s + " IS NOT NULL"); // NOSONAR java:S2077
    }

    public static List<Map<String, Object>> selectIdAndColumnLike(
            JdbcTemplate jdbc, String table, String column, String like) {
        String t = sur(table);
        String c = sur(column);
        return jdbc.queryForList("SELECT id, " + c + " AS ref FROM " + t + " WHERE " + c + " LIKE ?", like); // NOSONAR java:S2077
    }

    public static Integer countEquals(JdbcTemplate jdbc, String table, String column, String value) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + sur(table) + " WHERE " + sur(column) + " = ?", // NOSONAR java:S2077
                Integer.class, value);
    }

    public static void executeDdl(JdbcTemplate jdbc, String sql) {
        jdbc.execute(sql); // NOSONAR java:S2077 — identifiants déjà validés par sur()
    }

    public static int updateColumnById(JdbcTemplate jdbc, String table, String column, String value, Object id) {
        return jdbc.update(
                "UPDATE " + sur(table) + " SET " + sur(column) + " = ? WHERE id = ?", // NOSONAR java:S2077
                value, id);
    }
}
