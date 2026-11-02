package mr.gov.finances.sgci.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutMarche;

import mr.gov.finances.sgci.domain.enums.TypeMarche;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMarcheRequest {

    @NotNull(message = "La convention est obligatoire")
    private Long conventionId;

    /**
     * Toutes les conventions du marché, la principale en tête. Facultatif : absent, le marché n'a que
     * {@link #conventionId}. Présent, son premier élément devient la convention principale.
     */
    private java.util.List<Long> conventionIds;

    private Long demandeCorrectionId;

    @NotNull(message = "Le numéro de marché est obligatoire")
    private String numeroMarche;

    @NotBlank(message = "L'intitulé est obligatoire")
    private String intitule;

    private LocalDate dateSignature;

    @NotNull(message = "Le montant HT est obligatoire")
    @JsonProperty("montantContratHt")
    private BigDecimal montantContratHt;

    /**
     * Montant TTC, facultatif. Jusqu'au 02/11/2026 ce nom était un alias de {@code montantContratHt} :
     * un formulaire qui envoyait les deux voyait le TTC écraser le HT (anomalie A41).
     */
    private BigDecimal montantContratTtc;

    /** Fourniture, prestation ou travaux. Optionnel : rien ne permet de le déduire. */
    private TypeMarche typeMarche;


    @NotNull(message = "Le statut est obligatoire")
    private StatutMarche statut;
}
