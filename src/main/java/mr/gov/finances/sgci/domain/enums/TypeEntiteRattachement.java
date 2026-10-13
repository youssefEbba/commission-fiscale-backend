package mr.gov.finances.sgci.domain.enums;

/**
 * Nature de l'entité à laquelle une personne demande d'être rattachée.
 *
 * <p>Distinct de {@link Role} à dessein : le type dit <em>à quoi</em> la personne se rattache, le rôle
 * dit <em>en quelle qualité</em>. Une même autorité contractante accueille ainsi un agent
 * ({@code AUTORITE_CONTRACTANTE}) et ses délégués ({@code AUTORITE_UPM}, {@code AUTORITE_UEP}).
 */
public enum TypeEntiteRattachement {
    ENTREPRISE,
    AUTORITE_CONTRACTANTE
}
