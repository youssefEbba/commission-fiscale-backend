package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilisationCreditDto {

    private Long id;
    private TypeUtilisation type;
    private Instant dateDemande;
    private BigDecimal montant;
    private StatutUtilisation statut;
    private Instant dateLiquidation;
    private Long certificatCreditId;
    private Long entrepriseId;
}
