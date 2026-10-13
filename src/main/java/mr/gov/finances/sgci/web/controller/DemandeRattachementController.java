package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutDemandeRattachement;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DemandeRattachementService;
import mr.gov.finances.sgci.web.dto.DemandeRattachementDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * File des demandes de rattachement, instruite par le Président.
 *
 * <p>Une permission dédiée plutôt que {@code user.list} / {@code user.disable} : accepter qu'une
 * personne administre une entreprise n'est pas le même acte qu'activer un compte interne, et les
 * deux portes doivent pouvoir être confiées à des rôles différents depuis l'écran des rôles.
 */
@RestController
@RequestMapping("/api/demandes-rattachement")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DemandeRattachementController {

    private final DemandeRattachementService service;

    @GetMapping
    @PreAuthorize("hasAuthority('rattachement.validate')")
    public List<DemandeRattachementDto> lister(
            @RequestParam(required = false) StatutDemandeRattachement statut) {
        return service.lister(statut);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rattachement.validate')")
    public DemandeRattachementDto obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @PostMapping("/{id}/approuver")
    @PreAuthorize("hasAuthority('rattachement.validate')")
    public DemandeRattachementDto approuver(@PathVariable Long id,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.approuver(id, user);
    }

    @PostMapping("/{id}/refuser")
    @PreAuthorize("hasAuthority('rattachement.validate')")
    public DemandeRattachementDto refuser(@PathVariable Long id,
                                          @RequestParam String motif,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        return service.refuser(id, motif, user);
    }
}
