package mr.gov.finances.sgci.config;

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
}
