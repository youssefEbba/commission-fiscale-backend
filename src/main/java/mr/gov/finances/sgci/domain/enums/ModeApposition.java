package mr.gov.finances.sgci.domain.enums;

/**
 * Comment la signature et le cachet ont été apposés sur le document déposé.
 *
 * <p><b>Ce n'est pas une preuve.</b> Le serveur ne compose pas les documents — ils le sont dans le
 * navigateur — et reçoit un PDF opaque : il n'a aucun moyen de vérifier qu'une empreinte y a
 * effectivement été incrustée, ni laquelle. {@code APPOSE_SYSTEME} est donc une <em>déclaration</em>
 * du client, utile à l'exploitation et à l'audit, et non un élément de non-répudiation. Une valeur
 * juridiquement opposable exigerait une composition serveur et une signature numérique (PAdES).
 *
 * <p>{@code null} en base est la troisième réponse, et la plus fréquente : documents antérieurs à ce
 * champ, et toutes les pièces qui ne portent aucune signature.
 */
public enum ModeApposition {
    /** Modèle téléchargé, signé et cacheté à la main, puis scanné et téléversé. */
    MANUSCRIT_SCANNE,
    /** Empreintes enregistrées incrustées par le système au moment de la composition. */
    APPOSE_SYSTEME
}
