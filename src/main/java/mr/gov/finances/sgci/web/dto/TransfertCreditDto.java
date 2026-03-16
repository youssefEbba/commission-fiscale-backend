package mr.gov.finances.sgci.web.dto;

import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransfertCreditDto {

    private Long id;
    private Instant dateDemande;
    private Long certificatCreditId;
    private String certificatNumero;
    private Long entrepriseSourceId;
    private BigDecimal montant;
    private Boolean operationsDouaneCloturees;
    private StatutTransfert statut;
}
