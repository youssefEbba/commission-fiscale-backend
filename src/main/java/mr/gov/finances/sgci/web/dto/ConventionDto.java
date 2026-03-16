package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutConvention;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConventionDto {
    private Long id;
    private String reference;
    private String intitule;
    private String bailleur;
    private String bailleurDetails;
    private LocalDate dateSignature;
    private LocalDate dateFin;
    private BigDecimal montantDevise;
    private BigDecimal montantMru;
    private String deviseOrigine;
    private BigDecimal tauxChange;
    private StatutConvention statut;
    private Long autoriteContractanteId;
    private String autoriteContractanteNom;
    private Instant dateCreation;
    private Long valideParUserId;
    private Instant dateValidation;
    private String motifRejet;
    private List<DocumentConventionDto> documents;
}
