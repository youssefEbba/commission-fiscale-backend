package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRequirementDto {

    private Long id;
    private ProcessusDocument processus;
    private String codeDocument;
    private String libelle;
    private Boolean obligatoire;
    private Set<TypeFichierAutorise> typesAutorises;
    private String description;
    private Integer ordreAffichage;

    /**
     * Faux pour les pièces que la Commission produit elle-même pendant l'instruction.
     *
     * <p>Permet à l'écran de paramétrage d'afficher la famille de chaque code, là où le formulaire
     * de dépôt demande directement la liste filtrée.
     */
    private Boolean deposableParLeDemandeur;
}
