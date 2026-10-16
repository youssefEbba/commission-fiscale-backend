package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.ParametreService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Les réglages lisibles sans être connecté.
 *
 * <p>Nécessaire parce que le pied de page s'affiche sur la page de connexion : exiger un compte
 * pour lire une adresse de contact reviendrait à la laisser en dur dans l'écran, c'est-à-dire à ne
 * rien avoir paramétré.
 *
 * <p>Ne rend <b>que</b> les clés de la liste blanche tenue dans {@code ParametreService}, jamais la
 * table entière. C'est cette route qui justifie que la liste soit en dur dans le code et non un
 * drapeau en base : ici, une erreur s'expose à tout internet.
 */
@RestController
@RequestMapping("/api/public/parametres")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ParametrePublicController {

    private final ParametreService service;

    @GetMapping
    public Map<String, String> lirePubliques() {
        return service.lirePubliques();
    }
}
