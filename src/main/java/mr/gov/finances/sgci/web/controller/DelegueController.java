package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DelegueService;
import mr.gov.finances.sgci.web.dto.CreateDelegueRequest;
import mr.gov.finances.sgci.web.dto.UtilisateurDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/delegues")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DelegueController {

    private final DelegueService service;

    @GetMapping
    @PreAuthorize("hasAuthority('delegue.list')")
    public List<UtilisateurDto> getMyDelegues(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findMyDelegues(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('delegue.create')")
    public UtilisateurDto createDelegue(
            @Valid @RequestBody CreateDelegueRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.createDelegue(request, user);
    }

    @PatchMapping("/{id}/actif")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('delegue.disable')")
    public void setDelegueActif(
            @PathVariable Long id,
            @RequestParam boolean actif,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        service.setDelegueActif(id, actif, user);
    }
}
