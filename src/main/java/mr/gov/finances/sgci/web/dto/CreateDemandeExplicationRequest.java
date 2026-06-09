package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import mr.gov.finances.sgci.domain.enums.ContexteExplication;
import mr.gov.finances.sgci.domain.enums.Role;

@Data
public class CreateDemandeExplicationRequest {

    @NotNull
    private ContexteExplication contexte;

    @NotNull
    private Long dossierId;

    @NotNull
    private Role roleDestinataire;

    @NotBlank
    @Size(max = 2000)
    private String message;
}
