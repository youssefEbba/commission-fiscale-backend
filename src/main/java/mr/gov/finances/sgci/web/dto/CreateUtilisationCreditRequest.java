package mr.gov.finances.sgci.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeAchat;
import mr.gov.finances.sgci.domain.enums.TypeLigneTaxe;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUtilisationCreditRequest {

    @NotNull(message = "Le type (DOUANIER ou TVA_INTERIEURE) est obligatoire")
    private TypeUtilisation type;

    @NotNull(message = "Le certificat de crédit est obligatoire")
    private Long certificatCreditId;

    @NotNull(message = "L'entreprise est obligatoire")
    private Long entrepriseId;

    private BigDecimal montant;

    // ── Champs spécifiques utilisation douanière ─────────────────────────────

    private String numeroDeclaration;
    private String numeroBulletin;
    private Instant dateDeclaration;
    private Boolean enregistreeSYDONIA;

    /**
     * Lignes du bulletin de liquidation saisies par l'entreprise.
     * Obligatoires pour type DOUANIER ; ignorées pour TVA_INTERIEURE.
     */
    @Valid
    private List<LigneBulletinRequest> lignes;

    // ── Champs spécifiques utilisation TVA intérieure ────────────────────────

    private TypeAchat typeAchat;
    private String numeroFacture;
    private Instant dateFacture;
    private BigDecimal montantTVAInterieure;
    private String numeroDecompte;

    /** Si {@code true}, statut {@code BROUILLON} jusqu'à soumission explicite. */
    private Boolean brouillon;

    // ── Inner DTO ────────────────────────────────────────────────────────────

    /** Une ligne du bulletin saisie par l'entreprise. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LigneBulletinRequest {

        /** Code court de la taxe : "DD", "TVA", "RS", "PSC", "IMF", "PC", "TSI"… */
        @NotBlank(message = "Le code taxe est obligatoire")
        private String codeTaxe;

        /** Libellé complet tel qu'il apparaît sur le bulletin. */
        @NotBlank(message = "La dénomination taxe est obligatoire")
        private String denominationTaxe;

        /** GLOBALE ou ARTICLE */
        @NotNull(message = "Le type de ligne (GLOBALE ou ARTICLE) est obligatoire")
        private TypeLigneTaxe typeLigne;

        /** Valeur de la taxe en MRU. */
        @NotNull(message = "La valeur de la taxe est obligatoire")
        @PositiveOrZero
        private BigDecimal valeurTaxe;
    }
}
