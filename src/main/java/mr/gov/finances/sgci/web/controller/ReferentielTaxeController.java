package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.ReferentielTaxeService;
import mr.gov.finances.sgci.web.dto.CreateReferentielTaxeRequest;
import mr.gov.finances.sgci.web.dto.ReferentielTaxeDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API du référentiel des taxes douanières.
 * <ul>
 *   <li>Lecture (taxes actives) — accessible à tous les utilisateurs authentifiés.</li>
 *   <li>Administration (CRUD complet) — réservée aux rôles ADMIN_SI et PRESIDENT.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/referentiel-taxes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReferentielTaxeController {

    private final ReferentielTaxeService service;

    /**
     * Retourne les taxes actives triées par ordre d'affichage.
     * Utilisé par le formulaire de saisie du bulletin (entreprise).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ReferentielTaxeDto> getActives() {
        return service.findActives();
    }

    /**
     * Retourne toutes les taxes (actives + inactives).
     * Réservée à la console d'administration.
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('referentiel.taxe.manage')")
    public List<ReferentielTaxeDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ReferentielTaxeDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('referentiel.taxe.manage')")
    public ReferentielTaxeDto create(@Valid @RequestBody CreateReferentielTaxeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('referentiel.taxe.manage')")
    public ReferentielTaxeDto update(
            @PathVariable Long id,
            @Valid @RequestBody CreateReferentielTaxeRequest request
    ) {
        return service.update(id, request);
    }

    /** Désactive la taxe (soft delete) — elle n'apparaît plus dans les formulaires. */
    @PostMapping("/{id}/desactiver")
    @PreAuthorize("hasAuthority('referentiel.taxe.manage')")
    public ReferentielTaxeDto desactiver(@PathVariable Long id) {
        return service.desactiver(id);
    }

    /** Réactive une taxe désactivée. */
    @PostMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('referentiel.taxe.manage')")
    public ReferentielTaxeDto activer(@PathVariable Long id) {
        return service.activer(id);
    }

    /**
     * Suppression physique. À n'utiliser que si la taxe n'a jamais été utilisée
     * dans un bulletin. Sinon, préférer la désactivation.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('referentiel.taxe.manage')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
