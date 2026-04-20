package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDemandeCorrectionRequest {

    @NotNull(message = "L'autorité contractante est obligatoire")
    private Long autoriteContractanteId;

    @NotNull(message = "L'entreprise est obligatoire")
    private Long entrepriseId;

    @NotNull(message = "La convention est obligatoire")
    private Long conventionId;

    private Long marcheId;

    /**
     * Si {@code true}, statut {@code BROUILLON} : pas de notification, champs fiscaux optionnels jusqu'à soumission.
     */
    private Boolean brouillon;

    /** Ignoré si {@code brouillon != true} (sinon obligatoires en service). */
    private ModeleFiscalDto modeleFiscal;

    /** Ignoré si {@code brouillon != true}. */
    private DqeDto dqe;
}
