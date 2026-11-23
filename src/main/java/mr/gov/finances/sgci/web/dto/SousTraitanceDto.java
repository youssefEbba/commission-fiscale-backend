package mr.gov.finances.sgci.web.dto;

import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutSousTraitance;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SousTraitanceDto {

    private Long id;
    /** Référence lisible ({@code ST-…}) à afficher à la place de l'id. */
    private String reference;
    private Long certificatCreditId;
    private String certificatNumero;
    /** Référence lisible du crédit (CR-…), à afficher ; le numéro sert de code de vérification. */
    private String certificatReference;

    private Long entrepriseSourceId;

    private Long sousTraitantEntrepriseId;
    private String sousTraitantEntrepriseRaisonSociale;
    private String sousTraitantEntrepriseNif;

    private Boolean contratEnregistre;
    private BigDecimal volumes;
    private BigDecimal quantites;

    private Instant dateAutorisation;
    /** Date de la demande (dépôt), celle du récépissé. */
    private Instant dateDemande;
    private StatutSousTraitance statut;
}
