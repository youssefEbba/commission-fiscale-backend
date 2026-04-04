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
    private BigDecimal montantDroits;
    private BigDecimal montantTVADouane;
    private Boolean enregistreeSYDONIA;
    private BigDecimal soldeCordonAvant;
    private BigDecimal soldeCordonApres;

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
