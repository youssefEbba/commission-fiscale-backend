package mr.gov.finances.sgci.web.dto.provision;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionEntrepriseRef {

    private Long id;

    @Valid
    private EntrepriseDto create;
}
