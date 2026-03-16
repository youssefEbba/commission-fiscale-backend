package mr.gov.finances.sgci.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutReferentielProjet;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferentielProjetDto {
    private Long id;
    private String numero;
    private String nomProjet;
    private String administrateurProjet;
    private String referenceBciSecteur;
    private Instant dateDepot;
    private StatutReferentielProjet statut;
    private Long autoriteContractanteId;
    private String autoriteContractanteNom;
    private Long conventionId;
    private String conventionReference;
    private String conventionIntitule;
    private String conventionBailleur;
    private String conventionBailleurDetails;
    private LocalDate conventionDateSignature;
    private LocalDate conventionDateFin;
    private BigDecimal conventionMontantDevise;
    private BigDecimal conventionMontantMru;
    private String conventionDeviseOrigine;
    private BigDecimal conventionTauxChange;
    private Long valideParUserId;
    private Instant dateValidation;
    private String motifRejet;
    private List<DocumentProjetDto> documents;
}
