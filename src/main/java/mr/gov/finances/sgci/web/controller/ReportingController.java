package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ReportingService;
import mr.gov.finances.sgci.web.dto.reporting.ReportingSummaryDto;
import mr.gov.finances.sgci.web.dto.reporting.TimeSeriesPointDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Agrégats pour tableaux de bord et exports (périmètre selon le rôle).
 */
@RestController
@RequestMapping("/api/reporting")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReportingController {

    private final ReportingService reportingService;

    /**
     * Synthèse multi-domaines sur une fenêtre temporelle.
     * Rôles « nationaux » (Président, ADMIN_SI, DGD, DGTCP, DGI, DGB) peuvent restreindre avec
     * {@code autoriteContractanteId} et / ou {@code entrepriseId}.
     */
    @GetMapping("/summary")
    public ReportingSummaryDto summary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId
    ) {
        return reportingService.getSummary(user, from, to, autoriteContractanteId, entrepriseId);
    }

    /**
     * Série temporelle des demandes de correction (compte par mois civil).
     */
    @GetMapping("/timeseries/demandes")
    @PreAuthorize("hasAuthority('reporting.view')")
    public List<TimeSeriesPointDto> demandeTimeseries(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId
    ) {
        return reportingService.getDemandeTimeseries(user, from, to, autoriteContractanteId, entrepriseId);
    }
}
