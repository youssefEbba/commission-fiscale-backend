package mr.gov.finances.sgci.web.dto.provision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionCertificatCreditResponse {

    private Long certificatCreditId;
    private String certificatNumero;
    private String statut;
    private BigDecimal montantCordon;
    private BigDecimal montantTVAInterieure;
    private BigDecimal soldeCordon;
    private BigDecimal soldeTVA;
    private Long demandeCorrectionId;

    @Builder.Default
    private List<ProvisionWorkflowStepDto> steps = new ArrayList<>();
}
