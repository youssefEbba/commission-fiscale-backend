package mr.gov.finances.sgci.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class UpdateMarcheRequest {

    @NotNull(message = "Le numéro de marché est obligatoire")
    private String numeroMarche;

    /**
     * Délibérément sans {@code @NotBlank}, à la différence de {@link CreateMarcheRequest} : 23 des 27
     * marchés existants n'ont pas d'intitulé, et l'exiger ici les rendrait non modifiables tant que
     * personne ne leur en invente un. {@code MarcheService.update} refuse en revanche d'effacer un
     * intitulé déjà renseigné.
     */
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

    /**
     * Les conventions du marché, la principale en tête. Réservé au Président et à l'administrateur ;
     * absent, les conventions ne changent pas.
     */
    private java.util.List<Long> conventionIds;
}
