package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.EntrepriseService;
import mr.gov.finances.sgci.service.UtilisateurService;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;
import mr.gov.finances.sgci.web.dto.UtilisateurDto;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    private final EntrepriseService entrepriseService;

    /**
     * Liste tous les utilisateurs (réservé à l'administrateur / PRESIDENT).
     */
    @GetMapping
    @PreAuthorize("hasAuthority('user.list')")
    public List<UtilisateurDto> getAll() {
        return utilisateurService.findAll();
    }

    @GetMapping("/sous-traitants")
    @PreAuthorize("isAuthenticated()")
    public List<EntrepriseDto> getSousTraitants() {
        return entrepriseService.findAll();
    }

    /**
     * Liste des utilisateurs en attente de validation (actif = false).
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('user.list')")
    public List<UtilisateurDto> getPending() {
        return utilisateurService.findPending();
    }

    /**
     * Active ou désactive un compte utilisateur.
     */
    @PatchMapping("/{id}/actif")
    @PreAuthorize("hasAuthority('user.disable')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setActif(@PathVariable Long id, @RequestParam boolean actif) {
        utilisateurService.setActif(id, actif);
    }
}

