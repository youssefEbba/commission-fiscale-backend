package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Quittance DGI attestant le paiement de la TVA intérieure. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuittanceDgiDto {

    private Long id;
    private String numeroQuittance;
    private Instant dateQuittance;
    private BigDecimal montant;
    private String documentChemin;
    private String documentNomFichier;
    /** À passer à {@code GET /api/documents/{documentId}/download} pour récupérer le justificatif. */
    private Long documentId;
    private String deposeePar;
    private Instant dateDepot;
}
