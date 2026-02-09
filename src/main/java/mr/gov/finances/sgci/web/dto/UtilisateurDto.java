package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilisateurDto {

    private Long id;
    private String username;
    private Role role;
    private String nomComplet;
    private String email;
    private Boolean actif;
}

