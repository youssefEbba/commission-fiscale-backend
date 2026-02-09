package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutDemande;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeCorrectionDto {

    private Long id;
    private String numero;
    private Instant dateDepot;
    private StatutDemande statut;
    private Instant dateCreation;
    private Instant dateModification;
    private Long autoriteContractanteId;
    private String autoriteContractanteNom;
    @Builder.Default
    private List<DocumentDto> documents = new ArrayList<>();
}
