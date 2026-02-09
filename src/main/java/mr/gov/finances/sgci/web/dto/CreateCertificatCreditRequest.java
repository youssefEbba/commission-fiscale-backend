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

    private Instant dateValidite;

    @NotNull(message = "Le montant cordon est obligatoire")
    private BigDecimal montantCordon;

    private BigDecimal montantTVAInterieure;

    /**
     * Si null, initialisé à montantCordon et montantTVAInterieure.
     */
    private BigDecimal soldeCordon;
    private BigDecimal soldeTVA;
}
