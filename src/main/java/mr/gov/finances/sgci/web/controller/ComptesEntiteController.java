package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;
import mr.gov.finances.sgci.service.ComptesEntiteService;
import mr.gov.finances.sgci.web.dto.UtilisateurDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Les comptes rattachés à une entreprise ou à une autorité contractante, administrés depuis la fiche
 * de l'entité.
 *
 * <p>Protégé par {@code rattachement.validate}, la même permission que la file des demandes : décider
 * qui administre une entité est un seul et même pouvoir, qu'il s'exerce en approuvant une demande ou
 * en créant directement un compte. Les séparer aurait permis d'accorder l'un sans l'autre et de
 * contourner la file.
 */
@RestController
@RequestMapping("/api/entites/{type}/{entiteId}/comptes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ComptesEntiteController {

    private final ComptesEntiteService service;

    @GetMapping
    @PreAuthorize("hasAuthority('rattachement.validate')")
    public List<UtilisateurDto> lister(@PathVariable TypeEntiteRattachement type,
                                       @PathVariable Long entiteId) {
        return service.lister(type, entiteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('rattachement.validate')")
    public UtilisateurDto creer(@PathVariable TypeEntiteRattachement type,
                                @PathVariable Long entiteId,
                                @RequestParam String username,
                                @RequestParam String motDePasse,
                                @RequestParam Role role,
                                @RequestParam(required = false) String nomComplet,
                                @RequestParam(required = false) String email) {
        return service.creer(type, entiteId, username, motDePasse, role, nomComplet, email);
    }

    @PatchMapping("/{utilisateurId}/actif")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('rattachement.validate')")
    public void definirActif(@PathVariable TypeEntiteRattachement type,
                             @PathVariable Long entiteId,
                             @PathVariable Long utilisateurId,
                             @RequestParam boolean actif) {
        service.definirActif(type, entiteId, utilisateurId, actif);
    }
}
