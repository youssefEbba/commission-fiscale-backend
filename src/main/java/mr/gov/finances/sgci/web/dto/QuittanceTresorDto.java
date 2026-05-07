package mr.gov.finances.sgci.web.dto;

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
public class QuittanceTresorDto {

    private Long id;
    private String numeroQuittance;
    private Instant dateQuittance;
    private BigDecimal montant;
    private String referencePaiement;
    private Long utilisationDouaniereId;
    /** URL du justificatif dans le GED (null si non encore uploadé). */
    private String documentChemin;
    private String documentNomFichier;
}
