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
public class SignatureDto {

    private Long id;
    private Long utilisateurId;
    private String utilisateurNom;
    private Role role;
    private String nomAffiche;
    private String contentType;
    private Long taille;
    private Integer largeurPx;
    private Integer hauteurPx;
    private String checksumSha256;
    private Boolean active;
    private Integer version;
    private Instant dateCreation;
    private String creePar;
    private Instant dateDesactivation;
}
