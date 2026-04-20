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
    private String projectReference;
    private String intitule;
    private Long bailleurId;
    private String bailleurNom;
    /** Copie lecture seule de {@code Bailleur.details} (aucune colonne dupliquée sur la convention). */
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
    private Long creeParAutoriteContractanteId;
    private String creeParAutoriteContractanteNom;
    private Instant dateCreation;
    private Long valideParUserId;
    private Instant dateValidation;
    private String motifRejet;
    private List<DocumentConventionDto> documents;
}
