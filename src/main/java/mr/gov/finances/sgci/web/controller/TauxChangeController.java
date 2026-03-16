package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.TauxChangeService;
import mr.gov.finances.sgci.web.dto.TauxChangeResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/taux-change")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TauxChangeController {

    private final TauxChangeService service;

    @GetMapping
    @PreAuthorize("hasAuthority('taux_change.view')")
    public TauxChangeResponse getTaux(@RequestParam String devise) {
        BigDecimal taux = service.getTaux(devise);
        return TauxChangeResponse.builder()
                .devise(devise != null ? devise.trim().toUpperCase() : null)
                .base(service.getBase())
                .taux(taux)
                .source(service.getUrl())
                .build();
    }
}
