package mr.gov.finances.sgci.domain.modification;

/**
 * Autorisé, ou refusé avec son motif.
 *
 * <p>La raison est rédigée en français et destinée à l'utilisateur : elle est portée par la
 * politique et non reformulée dans chaque service, faute de quoi le même refus se dirait de six
 * façons différentes selon la porte d'entrée.
 *
 * <p>Elle prépare aussi, sans coût aujourd'hui, l'exposition de la modifiabilité au front — qui
 * affiche actuellement un bouton « Modifier » et laisse l'utilisateur découvrir le refus au moment
 * de l'enregistrement, faute qu'aucun DTO ne dise ce qui est modifiable.
 */
public record Verdict(boolean autorise, String raison) {

    private static final Verdict OK = new Verdict(true, null);

    public static Verdict ok() {
        return OK;
    }

    public static Verdict refus(String raison) {
        return new Verdict(false, raison);
    }

    public boolean refuse() {
        return !autorise;
    }
}
