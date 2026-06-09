package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.ContexteExplication;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DemandeExplicationService;
import mr.gov.finances.sgci.web.dto.CreateDemandeExplicationRequest;
import mr.gov.finances.sgci.web.dto.CreateExplicationMessageRequest;
import mr.gov.finances.sgci.web.dto.DemandeExplicationDto;
import mr.gov.finances.sgci.web.dto.DemandeExplicationMessageDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/demandes-explication")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DemandeExplicationController {

    private final DemandeExplicationService service;

    @GetMapping
    @PreAuthorize("hasAuthority('demande.explication.view')")
    public List<DemandeExplicationDto> list(
            @RequestParam ContexteExplication contexte,
            @RequestParam Long dossierId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.list(contexte, dossierId, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('demande.explication.create')")
    public DemandeExplicationDto create(
            @Valid @RequestBody CreateDemandeExplicationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.create(request, user);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('demande.explication.reply')")
    public DemandeExplicationMessageDto addMessage(
            @PathVariable Long id,
            @Valid @RequestBody CreateExplicationMessageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.addMessage(id, request.getMessage(), user);
    }

    @PutMapping("/{id}/fermer")
    @PreAuthorize("hasAuthority('demande.explication.close')")
    public DemandeExplicationDto fermer(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.fermer(id, user);
    }
}
