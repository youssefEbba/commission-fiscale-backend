package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutMarche;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarcheDto {

    private Long id;
    private Long conventionId;
    private Long demandeCorrectionId;
    private String numeroMarche;
    private LocalDate dateSignature;
    private BigDecimal montantContratTtc;
    private StatutMarche statut;
    private List<Long> delegueIds;
}
