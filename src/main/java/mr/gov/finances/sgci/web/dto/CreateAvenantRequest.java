package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeAvenant;

import java.math.BigDecimal;
import java.time.Instant;

/** Dépôt d'un avenant par l'entreprise ou l'autorité contractante. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAvenantRequest {

    @NotNull(message = "Le crédit d'impôt concerné est obligatoire")
    private Long certificatCreditId;

    @NotNull(message = "Le type d'avenant est obligatoire")
    private TypeAvenant type;

    private String motif;

    private Instant dateSignature;

    /** Obligatoires pour un avenant financier : le nouveau crédit extérieur et intérieur demandés. */
    private BigDecimal creditExterieurPropose;
    private BigDecimal creditInterieurPropose;
}
