package mr.gov.finances.sgci.web.dto;

import lombok.Getter;
import lombok.Setter;
import mr.gov.finances.sgci.domain.enums.StatutDemandeResetPassword;

import java.time.Instant;

@Getter
@Setter
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class DemandeResetPasswordDto {

    private Long id;
    private Long utilisateurId;
    private String username;
    private String nomComplet;
    private String email;
    private StatutDemandeResetPassword statut;
    private Instant dateCreation;
    private Instant dateTraitement;
    private Long traiteParId;
    private String traiteParUsername;
    private String motifRefus;
}
