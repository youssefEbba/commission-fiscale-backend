package mr.gov.finances.sgci.web.dto.archive;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Une utilisation de crédit lue dans le relevé d'archive. */
@Data
public class UtilisationArchiveDto {

    /** Libellé d'origine, ex. « UT 13 | 13/147/2022 ». */
    private String libelle;
    private String numeroQuittance;
    private Instant date;

    /** {@code true} = imputée sur le crédit douanier, {@code false} = sur la TVA intérieure. */
    private boolean douaniere;

    private BigDecimal montant;

    /** Somme des taxes imputées au crédit — doit égaler {@link #montant} sur une ligne douanière. */
    private BigDecimal totalPrisEnCharge;
    /** Somme des taxes restant à la charge de l'entreprise (hors crédit). */
    private BigDecimal totalAPayer;

    /** Ventilation fiscale ; vide pour une utilisation de TVA intérieure. */
    private List<LigneTaxeArchiveDto> lignesTaxe = new ArrayList<>();
}
