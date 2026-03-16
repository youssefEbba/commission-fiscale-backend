package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DossierGedService;
import mr.gov.finances.sgci.web.dto.DossierGedDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dossiers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DossierGedController {

    private final DossierGedService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<DossierGedDto> getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findAll(user);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public DossierGedDto getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findById(id, user);
    }
}
