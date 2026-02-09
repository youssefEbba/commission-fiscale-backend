package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.service.CertificatCreditService;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
import mr.gov.finances.sgci.web.dto.CreateCertificatCreditRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificats-credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CertificatCreditController {

    private final CertificatCreditService service;

    @GetMapping
    public List<CertificatCreditDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public CertificatCreditDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/by-entreprise/{entrepriseId}")
    public List<CertificatCreditDto> getByEntreprise(@PathVariable Long entrepriseId) {
        return service.findByEntreprise(entrepriseId);
    }

    @GetMapping("/by-statut")
    public List<CertificatCreditDto> getByStatut(@RequestParam StatutCertificat statut) {
        return service.findByStatut(statut);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CertificatCreditDto create(@Valid @RequestBody CreateCertificatCreditRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/statut")
    public CertificatCreditDto updateStatut(@PathVariable Long id, @RequestParam StatutCertificat statut) {
        return service.updateStatut(id, statut);
    }
}
