package mr.gov.finances.sgci.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.text.Normalizer;
import java.util.Locale;

/** Le sujet d'un retour d'usager : il sert à dégager les points forts et les difficultés. */
public enum CategorieRetour {
    /** Clarté des écrans, facilité de navigation. */
    ERGONOMIE,
    /** Rapidité, disponibilité de la plateforme. */
    PERFORMANCE,
    /** Les circuits et les pièces demandées. */
    PROCEDURE,
    /** Aide, accompagnement, réponses aux questions. */
    ASSISTANCE,
    AUTRE;

    /**
     * Lecture tolérante : le libellé saisi en clair (« Ergonomie », « Procédure ») vaut le code. Une
     * valeur vide vaut « pas de catégorie » ; une valeur inconnue est refusée, et le message d'erreur
     * liste les valeurs acceptées (A46).
     */
    @JsonCreator
    public static CategorieRetour depuisTexte(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String code = Normalizer.normalize(valeur.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT);
        for (CategorieRetour c : values()) {
            if (c.name().equals(code)) {
                return c;
            }
        }
        throw new IllegalArgumentException(valeur);
    }
}
