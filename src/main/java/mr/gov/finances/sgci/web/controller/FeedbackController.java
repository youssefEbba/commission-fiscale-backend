package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.FeedbackService;
import mr.gov.finances.sgci.web.dto.feedback.FeedbackDtos;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Feedback des usagers et enquêtes de satisfaction (cahier des charges, point 3).
 *
 * <p>Chacun donne son avis ({@code feedback.submit}) ; l'administrateur et le Président conçoivent
 * les enquêtes ({@code feedback.enquete.manage}) et lisent le tableau de bord consolidé
 * ({@code feedback.dashboard.view}).
 */
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService service;

    @PostMapping("/retours")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('feedback.submit')")
    public FeedbackDtos.RetourDto deposerRetour(@Valid @RequestBody FeedbackDtos.RetourRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return service.deposerRetour(request, user);
    }

    /** Les enquêtes ouvertes à mon profil, auxquelles je n'ai pas encore répondu. */
    @GetMapping("/enquetes/en-cours")
    @PreAuthorize("hasAuthority('feedback.submit')")
    public List<FeedbackDtos.EnqueteDto> enquetesEnCours(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.enquetesEnCours(user);
    }

    @PostMapping("/enquetes/{id}/reponses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('feedback.submit')")
    public void repondre(@PathVariable Long id, @Valid @RequestBody FeedbackDtos.ReponseEnqueteRequest request,
                         @AuthenticationPrincipal AuthenticatedUser user) {
        service.repondre(id, request, user);
    }

    @GetMapping("/enquetes")
    @PreAuthorize("hasAuthority('feedback.enquete.manage')")
    public List<FeedbackDtos.EnqueteDto> enquetes() {
        return service.listerEnquetes();
    }

    @GetMapping("/enquetes/{id}")
    @PreAuthorize("hasAuthority('feedback.enquete.manage')")
    public FeedbackDtos.EnqueteDto enquete(@PathVariable Long id) {
        return service.enquete(id);
    }

    @PostMapping("/enquetes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('feedback.enquete.manage')")
    public FeedbackDtos.EnqueteDto creerEnquete(@Valid @RequestBody FeedbackDtos.EnqueteRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return service.creerEnquete(request, user);
    }

    @PutMapping("/enquetes/{id}")
    @PreAuthorize("hasAuthority('feedback.enquete.manage')")
    public FeedbackDtos.EnqueteDto modifierEnquete(@PathVariable Long id,
                                                   @Valid @RequestBody FeedbackDtos.EnqueteRequest request) {
        return service.modifierEnquete(id, request);
    }

    @DeleteMapping("/enquetes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('feedback.enquete.manage')")
    public void supprimerEnquete(@PathVariable Long id) {
        service.supprimerEnquete(id);
    }

    @PostMapping("/enquetes/{id}/publier")
    @PreAuthorize("hasAuthority('feedback.enquete.manage')")
    public FeedbackDtos.EnqueteDto publier(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.publier(id, user);
    }

    @PostMapping("/enquetes/{id}/cloturer")
    @PreAuthorize("hasAuthority('feedback.enquete.manage')")
    public FeedbackDtos.EnqueteDto cloturer(@PathVariable Long id) {
        return service.cloturer(id);
    }

    /** La vague suivante d'une enquête périodique, en brouillon. */
    @PostMapping("/enquetes/{id}/relancer")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('feedback.enquete.manage')")
    public FeedbackDtos.EnqueteDto relancer(@PathVariable Long id,
                                            @Valid @RequestBody FeedbackDtos.RelanceRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.relancer(id, request, user);
    }

    /** Le tableau de bord consolidé : 365 derniers jours par défaut. */
    @GetMapping("/tableau-de-bord")
    @PreAuthorize("hasAuthority('feedback.dashboard.view')")
    public FeedbackDtos.TableauDeBord tableauDeBord(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.tableauDeBord(from, to);
    }
}
