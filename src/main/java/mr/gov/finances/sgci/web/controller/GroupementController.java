package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.GroupementService;
import mr.gov.finances.sgci.web.dto.GroupementDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groupements")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GroupementController {

    private final GroupementService service;

    @GetMapping
    @PreAuthorize("hasAuthority('groupement.list')")
    public List<GroupementDto> getAll(@RequestParam(required = false) Boolean actifs) {
        return service.findAll(actifs);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('groupement.list')")
    public GroupementDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('groupement.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupementDto create(@Valid @RequestBody GroupementDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('groupement.update')")
    public GroupementDto update(@PathVariable Long id, @Valid @RequestBody GroupementDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('groupement.delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteById(id);
    }
}
