package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.ParametreService;
import mr.gov.finances.sgci.web.dto.ParametreDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Le paramétrage de l'application, pour l'agent qui l'administre.
 *
 * <p>La lecture sans authentification est ailleurs, sous {@code /api/public/parametres} : séparer
 * les deux rend la surface publique visible d'un coup d'œil dans la configuration de sécurité, au
 * lieu de la cacher derrière un chemin parmi d'autres.
 */
@RestController
@RequestMapping("/api/parametres")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ParametreController {

    private final ParametreService service;

    @GetMapping
    @PreAuthorize("hasAuthority('parametre.manage')")
    public List<ParametreDto> lireToutes() {
        return service.lireToutes();
    }

    /**
     * Modifie un réglage existant.
     *
     * <p>Une clé inconnue est refusée et non créée : les réglages sont un référentiel fermé.
     */
    @PutMapping("/{cle}")
    @PreAuthorize("hasAuthority('parametre.manage')")
    public ParametreDto modifier(@PathVariable String cle, @Valid @RequestBody ModifierParametreRequest request) {
        return service.modifier(cle, request.getValeur());
    }

    @Data
    public static class ModifierParametreRequest {
        @Size(max = 512, message = "La valeur est limitée à 512 caractères")
        private String valeur;
    }
}
