package mr.gov.finances.sgci.web.dto.reporting;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.IndicateurReference;

import java.math.BigDecimal;
import java.time.Instant;

/** Valeur d'un indicateur avant la digitalisation ; sert aussi de corps à la saisie. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValeurReferenceDto {

    private Long id;

    @NotNull(message = "L'indicateur est obligatoire")
    private IndicateurReference code;

    private String libelle;

    private String unite;

    @NotNull(message = "La valeur est obligatoire")
    private BigDecimal valeur;

    private String periodeSource;

    private String commentaire;

    private Instant dateSaisie;
}
