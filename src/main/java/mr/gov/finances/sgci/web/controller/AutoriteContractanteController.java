package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.AutoriteContractanteService;
import mr.gov.finances.sgci.web.dto.AutoriteContractanteDto;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/autorites-contractantes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AutoriteContractanteController {

    private final AutoriteContractanteService service;

    @GetMapping
    public List<AutoriteContractanteDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AutoriteContractanteDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AutoriteContractanteDto create(@Valid @RequestBody AutoriteContractanteDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public AutoriteContractanteDto update(@PathVariable Long id, @Valid @RequestBody AutoriteContractanteDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteById(id);
    }
}
