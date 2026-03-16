package mr.gov.finances.sgci.web.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DossierEtapeGed {

    private String etape;
    private String label;
    private List<DocumentDto> documents;
}
