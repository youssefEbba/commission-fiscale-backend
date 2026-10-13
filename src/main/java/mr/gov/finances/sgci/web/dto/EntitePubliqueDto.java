package mr.gov.finances.sgci.web.dto;

/**
 * Entité telle que la voit le formulaire public de demande de rattachement : de quoi choisir dans
 * une liste, et rien de plus.
 *
 * <p>Volontairement réduit à deux champs. La route qui le sert est ouverte à tous : y laisser passer
 * un NIF, une adresse ou une situation fiscale en ferait un annuaire d'entreprises consultable sans
 * authentification.
 */
public record EntitePubliqueDto(Long id, String nom) {
}
