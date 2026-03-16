package mr.gov.finances.sgci.web.dto;

import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutSousTraitance;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SousTraitanceDto {

    private Long id;
    private Long certificatCreditId;
    private String certificatNumero;

    private Long entrepriseSourceId;

    private Long sousTraitantEntrepriseId;
    private String sousTraitantEntrepriseRaisonSociale;
    private String sousTraitantEntrepriseNif;

    private Boolean contratEnregistre;
    private BigDecimal volumes;
    private BigDecimal quantites;

    private Instant dateAutorisation;
    private StatutSousTraitance statut;
}
