package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeAchat;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUtilisationCreditRequest {

    @NotNull(message = "Le type (DOUANIER ou TVA_INTERIEURE) est obligatoire")
    private TypeUtilisation type;

    @NotNull(message = "Le certificat de crédit est obligatoire")
    private Long certificatCreditId;

    @NotNull(message = "L'entreprise est obligatoire")
    private Long entrepriseId;

    private BigDecimal montant;

    /** Champs spécifiques utilisation douanière */
    private String numeroDeclaration;
    private String numeroBulletin;
    private Instant dateDeclaration;
    private BigDecimal montantDroits;
    private BigDecimal montantTVA;
    private Boolean enregistreeSYDONIA;

    /** Champs spécifiques utilisation TVA intérieure */
    private TypeAchat typeAchat;
    private String numeroFacture;
    private Instant dateFacture;
    private BigDecimal montantTVAInterieure;
    private String numeroDecompte;
}
