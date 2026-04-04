package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatFinancialTotalsDto {
    private BigDecimal sumMontantCordon;
    private BigDecimal sumMontantTvaInterieure;
    private BigDecimal sumSoldeCordon;
    private BigDecimal sumSoldeTva;
    private long certificatCount;
}
