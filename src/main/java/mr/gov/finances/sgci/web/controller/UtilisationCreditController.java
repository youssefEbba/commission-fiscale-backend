package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utilisations-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UtilisationCreditController {

    private final UtilisationCreditService service;

    @GetMapping
    public List<UtilisationCreditDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public UtilisationCreditDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/by-certificat/{certificatCreditId}")
    public List<UtilisationCreditDto> getByCertificat(@PathVariable Long certificatCreditId) {
        return service.findByCertificatCreditId(certificatCreditId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UtilisationCreditDto create(@Valid @RequestBody CreateUtilisationCreditRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/statut")
    public UtilisationCreditDto updateStatut(@PathVariable Long id, @RequestParam StatutUtilisation statut) {
        return service.updateStatut(id, statut);
    }
}
