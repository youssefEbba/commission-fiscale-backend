package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatCreditDto {

    private Long id;
    private String numero;
    /** Référence lisible standardisée (ex. CR-01/2025). */
    private String reference;
    private Instant dateEmission;
    private Instant dateValidite;
    /** Date de mise en place effective (passage OUVERT). */
    private Instant dateMiseEnPlace;
    private BigDecimal montantCordon;
    private BigDecimal montantTVAInterieure;
    private BigDecimal soldeCordon;
    private BigDecimal soldeTVA;

    /** Récapitulatif fiscal (tableau d’attribution) — lignes (a) à (g). */
    private BigDecimal valeurDouaneFournitures;
    private BigDecimal droitsEtTaxesDouaneHorsTva;
    /** (d) accord initial — figé à la saisie. */
    private BigDecimal tvaImportationDouaneAccordee;
    /** Restant de la ligne (d) après liquidations douanières. */
    private BigDecimal tvaImportationDouane;
    private BigDecimal montantMarcheHt;
    private BigDecimal tvaCollecteeTravaux;

    /** Dérivé si (b) et (d) sont renseignés : crédit extérieur e = b + d (cohérent avec montantCordon). */
    private BigDecimal creditExterieurRecap;

    /** Dérivé si (g) et (d) sont renseignés : TVA nette intérieure h = g − d (cohérent avec montantTVAInterieure). */
    private BigDecimal creditInterieurNetRecap;

    /** e + h si les deux crédits dérivés sont calculables. */
    private BigDecimal totalCreditImpotRecap;

    private StatutCertificat statut;
    private Long entrepriseId;
    private String entrepriseRaisonSociale;

    private Long demandeCorrectionId;
    private Long marcheId;

    private String autoriteContractanteNom;
    /** Nom du ministère de tutelle de l'autorité contractante (affichage en-tête / PDF). */
    private String autoriteContractanteMinistereTutelleNom;
    /** Code du ministère de tutelle de l'autorité contractante. */
    private String autoriteContractanteMinistereTutelleCode;
}
