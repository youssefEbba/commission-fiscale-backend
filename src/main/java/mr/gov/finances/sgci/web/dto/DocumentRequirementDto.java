package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.SensDocument;
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
     * {@code ENTREE} : pièce fournie par le déposant, visible de lui à tout moment.
     * {@code SORTIE} : pièce produite par la Commission, remise seulement en fin de processus.
     * Absent, il vaut {@code ENTREE} — une pièce non classée reste visible.
     */
    private SensDocument sens;

    /**
     * Faux pour les pièces que le formulaire de dépôt ne propose pas : celles que la Commission
     * produit pendant l'instruction et, dans l'utilisation, celles des étapes du circuit.
     *
     * <p>Permet à l'écran de paramétrage d'afficher la famille de chaque code, là où le formulaire
     * de dépôt demande directement la liste filtrée.
     */
    private Boolean deposableParLeDemandeur;

    /**
     * Faux si la pièce a été désactivée dans ce processus : elle n'est plus demandée, mais reste
     * listée à l'écran de paramétrage pour pouvoir être réactivée.
     */
    private Boolean actif;

    /**
     * Vrai pour les pièces que le circuit exige nommément : elles ne se désactivent ni ne se
     * suppriment. L'écran de paramétrage doit griser ces deux actions.
     */
    private Boolean pieceDuCircuit;
}
