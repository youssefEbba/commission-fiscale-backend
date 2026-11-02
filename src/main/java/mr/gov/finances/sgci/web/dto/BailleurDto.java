package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.CategorieBailleur;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BailleurDto {
    private Long id;
    private String nom;
    private String details;
    /** NATIONAL, MULTILATERAL, BILATERAL, PRIVE ou NON_RENSEIGNE (défaut). */
    private CategorieBailleur categorie;
}
