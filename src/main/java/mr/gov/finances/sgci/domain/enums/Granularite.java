package mr.gov.finances.sgci.domain.enums;

/**
 * Le pas de temps d'une série de production.
 *
 * <p>Le trimestre n'est pas interrogé en base : il est la somme de ses trois mois, repliée en Java
 * par {@code CalendrierPeriodes}. La raison est portable plutôt qu'esthétique — le rendu de
 * {@code extract(quarter from …)} diverge entre H2, où tournent les tests, et MySQL, où tourne la
 * production.
 *
 * <p>Énumération purement Java, jamais persistée.
 */
public enum Granularite {

    MOIS,

    TRIMESTRE
}
