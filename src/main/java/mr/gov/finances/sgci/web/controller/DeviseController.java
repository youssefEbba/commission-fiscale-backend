package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.DeviseService;
import mr.gov.finances.sgci.web.dto.DeviseDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devises")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DeviseController {

    private final DeviseService service;

    @GetMapping
    @PreAuthorize("hasAuthority('devise.list')")
    public List<DeviseDto> getAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('devise.create')")
    public DeviseDto create(@Valid @RequestBody DeviseDto dto) {
        return service.create(dto);
    }
}
