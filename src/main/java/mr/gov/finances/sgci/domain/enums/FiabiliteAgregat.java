package mr.gov.finances.sgci.domain.enums;

/**
 * Ce que vaut une ligne d'agrégat, du point de vue de la confiance.
 *
 * <p>Publier un écart plutôt que le masquer est ce qui rend les autres chiffres croyables : une
 * Commission qui découvre un écart non annoncé cesse de croire l'ensemble du tableau.
 *
 * <p>Enumeration purement Java, jamais persistée.
 */
public enum FiabiliteAgregat {

    /** Les soldes et les mouvements concordent : rien à signaler. */
    CONFORME,

    /**
     * Un écart existe, et il s'explique entièrement.
     *
     * <p>Deux causes légitimes : les dossiers repris de l'ancienne application, dont les soldes ont
     * été importés sans mouvements, et les corrections manuelles d'administrateur tracées en
     * {@code ADMIN_CORRECTION}. L'écart n'est pas corrigeable, et ne doit pas être compensé.
     */
    EXPLIQUE,

    /** Un résidu inexpliqué subsiste. C'est la seule ligne qui mérite l'attention. */
    A_VERIFIER
}
