package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.SecteurActiviteService;
import mr.gov.finances.sgci.web.dto.SecteurActiviteDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Référentiel des secteurs d'activité des entreprises (suivi des bénéficiaires). */
@RestController
@RequestMapping("/api/secteurs-activite")
@RequiredArgsConstructor
public class SecteurActiviteController {

    private final SecteurActiviteService service;

    /** Lisible par tout compte : la liste alimente le formulaire de l'entreprise et les filtres. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SecteurActiviteDto> lister(@RequestParam(defaultValue = "false") boolean actifsSeulement) {
        return service.lister(actifsSeulement);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('referentiel.secteur.manage')")
    public SecteurActiviteDto creer(@Valid @RequestBody SecteurActiviteDto dto) {
        return service.creer(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('referentiel.secteur.manage')")
    public SecteurActiviteDto modifier(@PathVariable Long id, @Valid @RequestBody SecteurActiviteDto dto) {
        return service.modifier(id, dto);
    }
}
