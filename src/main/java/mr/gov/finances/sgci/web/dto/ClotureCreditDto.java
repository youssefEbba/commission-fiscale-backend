package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.MotifCloture;
import mr.gov.finances.sgci.domain.enums.TypeOperationCloture;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClotureCreditDto {

    private Long id;

    private Instant dateProposition;

    private Instant dateCloture;

    private MotifCloture motif;

    private TypeOperationCloture typeOperation;

    private BigDecimal soldeRestant;

    private Boolean approuvee;

    private Long certificatCreditId;

    private String certificatNumero;
}
