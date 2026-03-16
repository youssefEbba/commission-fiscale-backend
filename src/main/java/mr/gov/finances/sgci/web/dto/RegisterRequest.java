package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    private String username;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String password;

    @NotNull(message = "Le rôle est obligatoire")
    private Role role;

    private String nomComplet;
    private String email;
    private Long autoriteContractanteId;
    private Long entrepriseId;
    private String entrepriseRaisonSociale;
    private String entrepriseNif;
    private String entrepriseAdresse;
    private String entrepriseSituationFiscale;
}
