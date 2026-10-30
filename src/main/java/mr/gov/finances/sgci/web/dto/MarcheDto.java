package mr.gov.finances.sgci.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.domain.enums.TypeMarche;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarcheDto {

    private Long id;
    /** La convention principale ; {@link #conventionIds} les porte toutes, celle-ci en tête. */
    private Long conventionId;
    private List<Long> conventionIds;
    /** L'autorité contractante qui porte le marché. */
    private Long autoriteContractanteId;
    private String autoriteContractanteNom;
    private Long demandeCorrectionId;
    private String numeroMarche;
    /** Référence lisible standardisée (ex. DM-01/2025). */
    private String reference;
    /** Libellé / objet du marché (recherche & affichage). */
    private String intitule;
    private LocalDate dateSignature;
    /** Montant HT (nom JSON canonique ; l’ancien nom {@code montantContratTtc} est encore accepté en entrée). */
    @JsonProperty("montantContratHt")
    @JsonAlias("montantContratTtc")
    private BigDecimal montantContratHt;
    private StatutMarche statut;

    /** Fourniture, prestation ou travaux. {@code null} sur les marchés antérieurs. */
    private TypeMarche typeMarche;
    private List<Long> delegueIds;
}
