package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.BailleurService;
import mr.gov.finances.sgci.web.dto.BailleurDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bailleurs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BailleurController {

    private final BailleurService service;

    @GetMapping
    @PreAuthorize("hasAuthority('bailleur.list')")
    public List<BailleurDto> getAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('bailleur.create')")
    public BailleurDto create(@Valid @RequestBody BailleurDto dto) {
        return service.create(dto);
    }
}
