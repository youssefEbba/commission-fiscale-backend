package mr.gov.finances.sgci.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.archive.DgiNifClient;
import mr.gov.finances.sgci.web.dto.archive.VerificationNifDto;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nom d'une entreprise d'après son NIF, auprès du service « Vérifier NIF » de la DGI. Sert à
 * pré-remplir la création d'une entreprise : passer par le serveur évite au navigateur d'appeler
 * un site tiers.
 */
@RestController
@RequestMapping("/api/entreprises/verification-dgi")
@RequiredArgsConstructor
@Tag(name = "Entreprises")
public class VerificationNifController {

    private final DgiNifClient dgi;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('archive.import', 'entreprise.create')")
    @Operation(summary = "Vérifier un NIF auprès de la DGI (nom de l'entreprise, nom commercial)")
    public VerificationNifDto verifier(@RequestParam String nif) {
        return dgi.verifier(nif);
    }
}
