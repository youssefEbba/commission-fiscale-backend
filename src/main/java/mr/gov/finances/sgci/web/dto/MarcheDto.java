package mr.gov.finances.sgci.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutMarche;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarcheDto {

    private Long id;
    private Long conventionId;
    private Long demandeCorrectionId;
    private String numeroMarche;
    /** Libellé / objet du marché (recherche & affichage). */
    private String intitule;
    private LocalDate dateSignature;
    /** Montant HT (nom JSON canonique ; l’ancien nom {@code montantContratTtc} est encore accepté en entrée). */
    @JsonProperty("montantContratHt")
    @JsonAlias("montantContratTtc")
    private BigDecimal montantContratHt;
    private StatutMarche statut;
    private List<Long> delegueIds;
}
