package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatUtilisationEligibilityDto {

    private boolean eligible;
    private StatutCertificat statutCertificat;
    private List<String> motifs;
    private BigDecimal soldeCordon;
    private BigDecimal tvaImportationDouane;
    private BigDecimal soldeTVA;
    private boolean transfertExecute;
    private boolean clotureEnCours;
    private Instant dateValidite;
    private boolean expire;
}
