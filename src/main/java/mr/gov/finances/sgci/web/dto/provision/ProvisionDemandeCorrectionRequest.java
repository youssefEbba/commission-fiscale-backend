package mr.gov.finances.sgci.web.dto.provision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.web.dto.DqeDto;
import mr.gov.finances.sgci.web.dto.ModeleFiscalDto;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionDemandeCorrectionRequest {

    @NotNull
    @Valid
    private ProvisionAutoriteContractanteRef autoriteContractante;

    @NotNull
    @Valid
    private ProvisionEntrepriseRef entreprise;

    @NotNull
    @Valid
    private ProvisionConventionRef convention;

    @NotNull
    @Valid
    private ProvisionMarcheRef marche;

    @NotNull
    private ModeleFiscalDto modeleFiscal;

    @NotNull
    private DqeDto dqe;

    @Builder.Default
    private List<ProvisionDocumentUpload> documentsCorrection = new ArrayList<>();
}
