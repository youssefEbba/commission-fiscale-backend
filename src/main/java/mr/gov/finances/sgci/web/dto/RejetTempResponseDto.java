package mr.gov.finances.sgci.web.dto;

import lombok.*;
import mr.gov.finances.sgci.domain.enums.TypeDocument;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejetTempResponseDto {
    private Long id;
    private String message;
    private String documentUrl;
    private TypeDocument documentType;
    private Integer documentVersion;
    private Instant createdAt;
    private Long utilisateurId;
    private String utilisateurNom;
}
