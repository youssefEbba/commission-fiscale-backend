package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutDemande;

import java.math.BigDecimal;
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
    /** Référence lisible standardisée (ex. DC-001-01/2025). */
    private String reference;
    private Instant dateDepot;
    /** Intitulé libre du marché / convention saisi à la demande. */
    private String intituleMarche;
    /** Crédit intérieur demandé (0 → DGI hors workflow). */
    private BigDecimal creditInterieur;
    /** Crédit extérieur / douanier demandé (0 → DGD hors workflow). */
    private BigDecimal creditExterieur;
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
    /** Nom du ministère de tutelle de l'autorité contractante (affichage en-tête / PDF). */
    private String autoriteContractanteMinistereTutelleNom;
    /** Code du ministère de tutelle de l'autorité contractante. */
    private String autoriteContractanteMinistereTutelleCode;
    private Long entrepriseId;
    private String entrepriseRaisonSociale;
    /** NIF du titulaire effectif (chef de file si groupement, sinon NIF de l'entreprise). */
    private String entrepriseNif;
    /** Id du groupement porteur (null si demande individuelle). */
    private Long groupementId;
    private String groupementRaisonSociale;
    /** NIF affiché du groupement (= NIF du chef de file). */
    private String groupementNifAffiche;
    private Long conventionId;
    /** Référence de la convention liée (affichage listes). */
    private String conventionReference;
    /** Intitulé de la convention liée (affichage listes). */
    private String conventionIntitule;
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
