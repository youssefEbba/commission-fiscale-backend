package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Données du chèque certifié fourni par l'entreprise après visa DGD.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaisirChequeRequest {

    @NotBlank(message = "Le nom de la banque est obligatoire")
    private String banqueNom;

    @NotBlank(message = "Le numéro du chèque est obligatoire")
    private String numeroCheque;

    @NotNull(message = "Le montant du chèque est obligatoire")
    private BigDecimal montantCheque;

    /** Date d'émission du chèque certifié. */
    private Instant dateCheque;
}
