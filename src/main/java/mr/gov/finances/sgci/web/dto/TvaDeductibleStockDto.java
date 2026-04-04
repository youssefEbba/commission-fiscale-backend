package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TvaDeductibleStockDto {

    private Long id;

    /** Identifiant de l'importation (UtilisationDouaniere) qui a généré cette tranche */
    private Long utilisationDouaneId;

    /** Numéro de déclaration douanière source */
    private String numeroDeclaration;

    /** Montant initial créé lors de la liquidation douanière */
    private BigDecimal montantInitial;

    /** Montant restant disponible (après consommations FIFO) */
    private BigDecimal montantRestant;

    /** Montant déjà consommé par des apurements TVA intérieure */
    private BigDecimal montantConsomme;

    /** Date de création de cette tranche (détermine l'ordre FIFO) */
    private Instant dateCreation;

    /** true si cette tranche est entièrement consommée */
    private boolean epuise;
}
