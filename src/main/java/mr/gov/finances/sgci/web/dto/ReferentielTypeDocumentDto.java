package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferentielTypeDocumentDto {

    private String code;
    private String libelle;
    private String libelleAr;
    private Boolean actif;
    private Boolean systeme;
}
