package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.MotifCloture;
import mr.gov.finances.sgci.domain.enums.TypeOperationCloture;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClotureCreditDto {

    private Long id;

    private Instant dateProposition;

    private Instant dateCloture;

    private MotifCloture motif;

    private TypeOperationCloture typeOperation;

    private BigDecimal soldeRestant;

    private Boolean approuvee;

    private Long certificatCreditId;

    private String certificatNumero;

    /**
     * Vrai si la demande de correction d'origine déclarait des équipements sous régime (plaques TT).
     *
     * <p>C'est à la clôture que l'information compte : fermer un crédit dont des équipements restent
     * sous régime est précisément ce que la Commission veut être en mesure de voir avant de signer.
     */
    private Boolean equipementsPresents;

    /**
     * La demande de correction d'origine, pour que l'écran atteigne la liste des équipements.
     *
     * <p>Les pièces sont rattachées à la demande, pas au certificat : l'écran de clôture a besoin de
     * cet identifiant pour ouvrir la liste sans rappeler d'abord le certificat.
     */
    private Long demandeCorrectionId;
}
