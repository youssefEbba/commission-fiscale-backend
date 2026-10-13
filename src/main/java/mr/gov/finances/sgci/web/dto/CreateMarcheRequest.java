package mr.gov.finances.sgci.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutMarche;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMarcheRequest {

    @NotNull(message = "La convention est obligatoire")
    private Long conventionId;

    private Long demandeCorrectionId;

    @NotNull(message = "Le numéro de marché est obligatoire")
    private String numeroMarche;

    @NotBlank(message = "L'intitulé est obligatoire")
    private String intitule;

    private LocalDate dateSignature;

    @NotNull(message = "Le montant HT est obligatoire")
    @JsonProperty("montantContratHt")
    @JsonAlias("montantContratTtc")
    private BigDecimal montantContratHt;

    @NotNull(message = "Le statut est obligatoire")
    private StatutMarche statut;
}
