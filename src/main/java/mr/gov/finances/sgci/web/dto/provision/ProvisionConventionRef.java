package mr.gov.finances.sgci.web.dto.provision;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.web.dto.CreateConventionRequest;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionConventionRef {

    private Long id;

    @Valid
    private CreateConventionRequest create;

    @Builder.Default
    private List<ProvisionReferentialDocument> documents = new ArrayList<>();
}
