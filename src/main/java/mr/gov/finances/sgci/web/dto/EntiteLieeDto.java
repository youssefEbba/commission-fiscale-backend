package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Une entité reliée, réduite à ce qu'un écran affiche : autorité d'une convention, bailleur… */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntiteLieeDto {
    private Long id;
    private String nom;
}
