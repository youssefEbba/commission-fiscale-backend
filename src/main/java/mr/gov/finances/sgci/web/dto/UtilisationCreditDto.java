package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeAchat;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilisationCreditDto {

    private Long id;
    private TypeUtilisation type;
    private Instant dateDemande;
    private BigDecimal montant;
    private StatutUtilisation statut;
    private Instant dateLiquidation;
    private Long certificatCreditId;
    private Long entrepriseId;

    /** Entreprise titulaire du certificat (bénéficiaire du crédit), distincte du demandeur si sous-traitance. */
    private Long certificatTitulaireEntrepriseId;
    private String certificatTitulaireRaisonSociale;

    /**
     * {@code true} lorsque la demande est portée par l’entreprise sous-traitante (entreprise demandeuse
     * ≠ titulaire du certificat).
     */
    private Boolean demandeurEstSousTraitant;

    /** Champs spécifiques utilisation douanière */
    private String numeroDeclaration;
    private String numeroBulletin;
    private Instant dateDeclaration;
    private Boolean enregistreeSYDONIA;
    private BigDecimal soldeCordonAvant;
    private BigDecimal soldeCordonApres;

    /** Lignes du bulletin saisies par l'entreprise, annotées par le DGD. */
    private List<LigneBulletinDto> lignes;

    /** Chèque certifié fourni par l'entreprise (après visa DGD). */
    private String banqueNom;
    private String numeroCheque;
    private BigDecimal montantCheque;
    private Instant dateCheque;

    /** Quittances Trésor enregistrées par le DGTCP. */
    private List<QuittanceTresorDto> quittances;

    /** Total pris en charge par le CI (somme des lignes AU_CI). Renseigné après liquidation. */
    private BigDecimal totalPrisEnCharge;

    /** Total à payer comptant (somme des lignes A_PAYER). Renseigné après liquidation. */
    private BigDecimal totalAPayer;

    /** Part TVA des lignes AU_CI – pour info comptable. Renseigné après liquidation. */
    private BigDecimal montantTVADouane;

    /** Part hors-TVA des lignes AU_CI – pour info comptable. Renseigné après liquidation. */
    private BigDecimal montantDroits;

    /** Champs spécifiques utilisation TVA intérieure */
    private TypeAchat typeAchat;
    private String numeroFacture;
    private Instant dateFacture;
    private BigDecimal montantTVAInterieure;
    private String numeroDecompte;

    private BigDecimal tvaDeductibleUtilisee;
    private BigDecimal tvaNette;
    private BigDecimal creditInterieurUtilise;
    private BigDecimal paiementEntreprise;
    private BigDecimal reportANouveau;
    private BigDecimal soldeTVAAvant;
    private BigDecimal soldeTVAApres;
}
