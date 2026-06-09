package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.SousTraitanceService;
import mr.gov.finances.sgci.service.UtilisateurService;
import mr.gov.finances.sgci.web.dto.ChangeMyPasswordRequest;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;
import mr.gov.finances.sgci.web.dto.UpdateMyProfileRequest;
import mr.gov.finances.sgci.web.dto.UpdateUtilisateurRequest;
import mr.gov.finances.sgci.web.dto.UtilisateurDto;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    private final SousTraitanceService sousTraitanceService;

    /**
     * Profil de l'utilisateur connecté.
     */
    @GetMapping("/me")
    public UtilisateurDto getMyProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return utilisateurService.findMyProfile(user);
    }

    /**
     * Mise à jour du profil de l'utilisateur connecté (nom complet, e-mail).
     */
    @PatchMapping("/me")
    public UtilisateurDto updateMyProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        return utilisateurService.updateMyProfile(user, request);
    }

    /**
     * Changement de mot de passe par l'utilisateur connecté (mot de passe actuel requis).
     */
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeMyPassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangeMyPasswordRequest request
    ) {
        utilisateurService.changeMyPassword(user, request);
    }

    /**
     * Liste tous les utilisateurs (réservé à l'administrateur / PRESIDENT).
     */
    @GetMapping
    @PreAuthorize("hasAuthority('user.list')")
    public List<UtilisateurDto> getAll() {
        return utilisateurService.findAll();
    }

    /**
     * Détail d'un utilisateur.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user.list')")
    public UtilisateurDto getById(@PathVariable Long id) {
        return utilisateurService.findById(id);
    }

    /**
     * Modification d'un compte utilisateur (identité, rattachements, mot de passe, rôle).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user.update')")
    public UtilisateurDto update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUtilisateurRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return utilisateurService.update(id, request, user);
    }

    /**
     * Entreprises sous-traitantes (référencées sur des sous-traitances), pas l’ensemble du référentiel entreprises.
     * Titulaire : sous-traitants liés à ses certificats ; autres rôles autorisés : vue globale distincte.
     */
    @GetMapping("/sous-traitants")
    @PreAuthorize("hasAnyAuthority('sous_traitant.list', 'sous_traitance.dgtcp.queue.view', 'user.list')")
    public List<EntrepriseDto> getSousTraitants(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            return List.of();
        }
        if (user.getRole() == Role.ENTREPRISE) {
            return sousTraitanceService.findSousTraitantEntreprisesForTitulaire(user);
        }
        if (user.getRole() == Role.SOUS_TRAITANT) {
            return List.of();
        }
        return sousTraitanceService.findDistinctSousTraitantEntreprisesGlobally();
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
