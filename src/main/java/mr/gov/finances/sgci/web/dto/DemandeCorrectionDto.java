package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutDemande;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeCorrectionDto {

    private Long id;
    private String numero;
    private Instant dateDepot;
    private StatutDemande statut;
    private boolean validationDgd;
    private boolean validationDgtcp;
    private boolean validationDgi;
    private boolean validationDgb;
    private Long validationDgdUserId;
    private Instant validationDgdDate;
    private Long validationDgtcpUserId;
    private Instant validationDgtcpDate;
    private Long validationDgiUserId;
    private Instant validationDgiDate;
    private Long validationDgbUserId;
    private Instant validationDgbDate;
    private String motifRejet;
    private Instant dateCreation;
    private Instant dateModification;
    private Long autoriteContractanteId;
    private String autoriteContractanteNom;
    private Long entrepriseId;
    private String entrepriseRaisonSociale;
    private Long conventionId;
    /**
     * Id du marché lié à la demande (attribution / adjudication), comme {@code entrepriseId} / {@code conventionId}.
     * Redondant avec {@link #marche}{@code .id} lorsque le marché est chargé — pratique pour les wizards sans lecture de l’objet imbriqué.
     */
    private Long marcheId;
    /** Marché détaché après annulation — id conservé pour la traçabilité. */
    private Long marcheIdTrace;

    /**
     * Si {@code statut == ANNULEE} : {@code true} si la réactivation (→ RECUE) est possible au regard du marché
     * tracé ({@link #marcheIdTrace}) — libre ou déjà lié à cette demande ; {@code false} si un autre dossier
     * occupe ce marché. {@code null} si non applicable (autre statut).
     */
    private Boolean marcheReactivable;
    private ModeleFiscalDto modeleFiscal;
    private DqeDto dqe;
    private MarcheDto marche;
    @Builder.Default
    private List<DocumentDto> documents = new ArrayList<>();
    @Builder.Default
    private List<DemandeCorrectionRejetDto> rejets = new ArrayList<>();
    @Builder.Default
    private List<DecisionCorrectionDto> decisions = new ArrayList<>();
}
