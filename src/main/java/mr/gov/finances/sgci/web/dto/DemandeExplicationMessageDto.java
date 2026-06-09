package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeExplicationMessageDto {

    private Long id;
    private String message;
    private Long auteurId;
    private String auteurNom;
    private Role roleAuteur;
    private Instant createdAt;
}
