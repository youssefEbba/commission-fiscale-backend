package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatCreditDto {

    private Long id;
    private String numero;
    private Instant dateEmission;
    private Instant dateValidite;
    private BigDecimal montantCordon;
    private BigDecimal montantTVAInterieure;
    private BigDecimal soldeCordon;
    private BigDecimal soldeTVA;
    private StatutCertificat statut;
    private Long entrepriseId;
    private String entrepriseRaisonSociale;

    private Long demandeCorrectionId;
    private Long marcheId;
}
