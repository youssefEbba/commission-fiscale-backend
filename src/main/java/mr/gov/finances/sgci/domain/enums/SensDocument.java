package mr.gov.finances.sgci.domain.enums;

/**
 * De quel côté du dossier une pièce se trouve : fournie, ou produite.
 *
 * <p>La distinction existait déjà dans le code, à trois endroits et sous trois formes — les pièces
 * « déposées par le demandeur » du formulaire de dépôt, les pièces « d'administration » masquées aux
 * utilisations, et l'étagement de la GED. Aucune n'était paramétrable, et aucune ne gouvernait ce
 * que le déposant voit réellement. Elle devient ici une donnée, portée par le référentiel des
 * pièces exigées et administrée dans l'écran de configuration de la GED.
 */
public enum SensDocument {

    /**
     * Fournie par le déposant, et visible de lui à tout moment.
     *
     * <p>C'est aussi la valeur retenue quand rien n'a été paramétré : une pièce dont personne n'a
     * tranché le sens reste visible. Le défaut penche du côté de la transparence, et il faut un
     * geste délibéré pour fermer une pièce.
     */
    ENTREE,

    /**
     * Produite par la Commission, et remise au déposant seulement à la fin du processus.
     *
     * <p>L'offre corrigée et la lettre d'adoption après l'adoption de la demande ; le certificat
     * après l'ouverture du crédit ; les quittances, le bulletin annoté et le certificat
     * d'utilisation après la liquidation. Le statut qui ouvre les pièces est propre au circuit, et
     * vit dans {@code OuvertureDocumentsPolicy} — pas ici.
     */
    SORTIE
}
