package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.EntrepriseService;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entreprises")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntrepriseController {

    private final EntrepriseService service;

    @GetMapping
    public List<EntrepriseDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public EntrepriseDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntrepriseDto create(@Valid @RequestBody EntrepriseDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public EntrepriseDto update(@PathVariable Long id, @Valid @RequestBody EntrepriseDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteById(id);
    }
}
