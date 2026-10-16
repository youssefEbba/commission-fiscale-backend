package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.maincourante.MainCouranteService;
import mr.gov.finances.sgci.web.dto.maincourante.MainCouranteDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La main courante de l'utilisateur connecte.
 *
 * <p>Aucune permission exigee au-dela de l'authentification, et c'est deliberе : la route ne rend
 * que ce que l'appelant a deja le droit de voir, cadre par le meme perimetre que le reporting. Une
 * permission dediee aurait eu pour seul effet d'empecher un role d'y voir sa propre liste de
 * travail, alors que son contenu est precisement borne a ce role.
 */
@RestController
@RequestMapping("/api/main-courante")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MainCouranteController {

    private final MainCouranteService service;

    @GetMapping
    public MainCouranteDto pourMoi(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.pourMoi(user);
    }
}
