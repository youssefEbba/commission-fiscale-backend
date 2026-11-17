package mr.gov.finances.sgci.domain.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les numéros de déclaration (douane) ou de facture (TVA intérieure) d'une utilisation : une
 * utilisation peut en porter plusieurs (remarque 7 de la recette).
 *
 * <p>Le premier numéro reste recopié dans l'ancien champ unique ({@code numeroDeclaration},
 * {@code numeroFacture}) : l'import d'archive, le stock de TVA déductible, les exports et les
 * écrans qui n'affichent qu'un numéro continuent de le lire.
 */
public final class NumerosJustificatifs {

    private NumerosJustificatifs() {
    }

    /**
     * Les numéros d'une demande : la liste si elle est fournie, sinon l'ancien champ seul. Espaces de
     * bordure retirés, vides écartés, doublons (sans tenir compte de la casse) retirés, ordre conservé.
     */
    public static List<String> depuis(List<String> liste, String seul) {
        List<String> source = liste != null && !liste.isEmpty() ? liste : (seul != null ? List.of(seul) : List.of());
        Map<String, String> uniques = new LinkedHashMap<>();
        for (String n : source) {
            if (n == null) {
                continue;
            }
            String nettoye = n.trim();
            if (!nettoye.isEmpty()) {
                uniques.putIfAbsent(nettoye.toUpperCase(), nettoye);
            }
        }
        return new ArrayList<>(uniques.values());
    }

    /** Les numéros enregistrés, ou l'ancien numéro seul pour une utilisation d'avant la liste. */
    public static List<String> effectifs(List<String> liste, String seul) {
        if (liste != null && !liste.isEmpty()) {
            return List.copyOf(liste);
        }
        return seul != null && !seul.isBlank() ? List.of(seul) : List.of();
    }
}
