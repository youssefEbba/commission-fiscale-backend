package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DelegueService;
import mr.gov.finances.sgci.service.MarcheService;
import mr.gov.finances.sgci.web.dto.CreateDelegueRequest;
import mr.gov.finances.sgci.web.dto.MarcheDto;
import mr.gov.finances.sgci.web.dto.SyncDelegueMarchesRequest;
import mr.gov.finances.sgci.web.dto.UpdateDelegueRequest;
import mr.gov.finances.sgci.web.dto.UtilisateurDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/delegues")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DelegueController {

    private final DelegueService service;
    private final MarcheService marcheService;

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

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('delegue.list')")
    public UtilisateurDto getById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.findById(id, user);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('delegue.update')")
    public UtilisateurDto updateDelegue(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDelegueRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.updateDelegue(id, request, user);
    }

    @GetMapping("/{id}/marches")
    @PreAuthorize("hasAuthority('delegue.list')")
    public List<MarcheDto> listMarches(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return marcheService.findMarchesForDelegue(id, user);
    }

    @PutMapping("/{id}/marches")
    @PreAuthorize("hasAuthority('marche.manage')")
    public List<MarcheDto> syncMarches(
            @PathVariable Long id,
            @RequestBody(required = false) SyncDelegueMarchesRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<Long> ids = request != null && request.getMarcheIds() != null
                ? request.getMarcheIds()
                : Collections.emptyList();
        return marcheService.syncDelegueMarches(id, ids, user);
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
