package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.ForexService;
import mr.gov.finances.sgci.web.dto.ForexConvertResponse;
import mr.gov.finances.sgci.web.dto.ForexRateResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/forex")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ForexController {

    private final ForexService service;

    @GetMapping("/convert")
    @PreAuthorize("hasAuthority('taux_change.view')")
    public ForexConvertResponse convert(@RequestParam String from,
                                        @RequestParam String to,
                                        @RequestParam BigDecimal amount) {
        BigDecimal result = service.convert(from, to, amount);
        return ForexConvertResponse.builder()
                .from(from != null ? from.trim().toUpperCase() : null)
                .to(to != null ? to.trim().toUpperCase() : null)
                .amount(amount)
                .result(result)
                .source(service.getBaseUrl() + "/convert")
                .build();
    }

    @GetMapping("/rate")
    @PreAuthorize("hasAuthority('taux_change.view')")
    public ForexRateResponse rate(@RequestParam String from,
                                  @RequestParam String to) {
        BigDecimal rate = service.getRate(from, to);
        return ForexRateResponse.builder()
                .from(from != null ? from.trim().toUpperCase() : null)
                .to(to != null ? to.trim().toUpperCase() : null)
                .rate(rate)
                .source(service.getBaseUrl() + "/convert")
                .build();
    }
}
