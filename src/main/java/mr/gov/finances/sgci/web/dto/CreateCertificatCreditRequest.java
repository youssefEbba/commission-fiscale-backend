package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
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
public class CreateCertificatCreditRequest {

    @NotNull(message = "L'entreprise est obligatoire")
    private Long entrepriseId;

    private Long lettreCorrectionId;

    private Long demandeCorrectionId;

    private Instant dateValidite;

    private BigDecimal montantCordon;

    private BigDecimal montantTVAInterieure;

    /** Récap. fiscal optionnel — voir {@link CertificatCreditDto}. */
    private BigDecimal valeurDouaneFournitures;
    private BigDecimal droitsEtTaxesDouaneHorsTva;
    private BigDecimal tvaImportationDouane;
    private BigDecimal montantMarcheHt;
    private BigDecimal tvaCollecteeTravaux;

    /**
     * Si null, initialisé à montantCordon et montantTVAInterieure.
     */
    private BigDecimal soldeCordon;
    private BigDecimal soldeTVA;

    /**
     * Si {@code true}, statut initial {@code BROUILLON}. Sinon statut {@code ENVOYEE} (envoyée, en attente de prise en charge
     * par DGI/DGD/DGTCP puis passage en {@code EN_CONTROLE}).
     */
    private Boolean brouillon;
}
