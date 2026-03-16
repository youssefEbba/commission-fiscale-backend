package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeAchat;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilisationCreditDto {

    private Long id;
    private TypeUtilisation type;
    private Instant dateDemande;
    private BigDecimal montant;
    private StatutUtilisation statut;
    private Instant dateLiquidation;
    private Long certificatCreditId;
    private Long entrepriseId;

    /** Champs spécifiques utilisation douanière */
    private String numeroDeclaration;
    private String numeroBulletin;
    private Instant dateDeclaration;
    private BigDecimal montantDroits;
    private BigDecimal montantTVADouane;
    private Boolean enregistreeSYDONIA;

    /** Champs spécifiques utilisation TVA intérieure */
    private TypeAchat typeAchat;
    private String numeroFacture;
    private Instant dateFacture;
    private BigDecimal montantTVAInterieure;
    private String numeroDecompte;
}
