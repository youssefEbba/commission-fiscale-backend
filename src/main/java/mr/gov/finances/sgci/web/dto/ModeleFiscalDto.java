package mr.gov.finances.sgci.web.dto;

import lombok.*;
import mr.gov.finances.sgci.domain.enums.TypeProjet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModeleFiscalDto {
    private Long id;
    private String referenceDossier;
    private TypeProjet typeProjet;
    private Boolean afficherNomenclature;
    private Instant dateCreation;
    private Instant dateModification;
    @Builder.Default
    private List<LigneImportationDto> importations = new ArrayList<>();
    private FiscaliteInterieureDto fiscaliteInterieure;
    private RecapitulatifDto recapitulatif;
}
