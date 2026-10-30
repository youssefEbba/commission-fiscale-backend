package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutAvenant;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.TypeAvenant;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvenantDto {
    private Long id;
    private String numero;
    private TypeAvenant type;
    private StatutAvenant statut;
    private String motif;
    private Instant dateSignature;

    private Long certificatCreditId;
    private String certificatNumero;
    private String certificatReference;
    private Long entrepriseId;
    private String entrepriseNom;
    private Long autoriteContractanteId;
    private String autoriteContractanteNom;

    private Long deposeParUtilisateurId;
    private Instant dateDepot;
    private BigDecimal creditExterieurPropose;
    private BigDecimal creditInterieurPropose;

    /** La demande de correction déclenchée par un avenant financier, et où elle en est. */
    private Long demandeCorrectionId;
    private String demandeCorrectionReference;
    private StatutDemande demandeCorrectionStatut;

    private Instant dateExamen;
    private Instant dateValidation;
    private Instant dateApplication;
    private String motifRejet;

    private BigDecimal montantCordonAvant;
    private BigDecimal montantCordonApres;
    private BigDecimal montantTVAInterieureAvant;
    private BigDecimal montantTVAInterieureApres;
    private BigDecimal soldeCordonAvant;
    private BigDecimal soldeCordonApres;
    private BigDecimal soldeTVAAvant;
    private BigDecimal soldeTVAApres;
    private BigDecimal tvaImportationRestanteAvant;
    private BigDecimal tvaImportationRestanteApres;
}
