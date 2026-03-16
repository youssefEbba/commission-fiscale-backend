package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutMarche;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMarcheRequest {

    @NotNull(message = "Le numéro de marché est obligatoire")
    private String numeroMarche;

    @NotNull(message = "La date de signature est obligatoire")
    private LocalDate dateSignature;

    @NotNull(message = "Le montant TTC est obligatoire")
    private BigDecimal montantContratTtc;

    @NotNull(message = "Le statut est obligatoire")
    private StatutMarche statut;
}
