package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Le recensement des bénéficiaires des crédits d'impôt (cahier des charges, point 4) : combien, de
 * quelle nature, dans quels secteurs, sur quels financements, et leur évolution par année fiscale.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficiairesDto {

    private Instant from;
    private Instant to;

    /** Entreprises titulaires d'au moins un crédit mis en place dans la période. */
    private long totalBeneficiaires;
    private long totalCredits;
    private BigDecimal montantAccorde;
    private BigDecimal montantConsomme;

    /** Entreprises nationales / étrangères. */
    private List<Ventilation> parNature;
    /** PME / grandes entreprises / non renseignée. */
    private List<Ventilation> parCategorie;
    private List<Ventilation> parSecteur;
    /** Fonds nationaux / partenaires extérieurs / non renseigné, d'après les bailleurs de la convention. */
    private List<Ventilation> parFinancement;
    private List<Annee> parAnnee;

    private List<String> definitions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Ventilation {
        private String cle;
        private String libelle;
        private long nbBeneficiaires;
        private long nbCredits;
        private BigDecimal montantAccorde;
        private BigDecimal montantConsomme;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Annee {
        private int annee;
        /** Entreprises dont le premier crédit de la période est mis en place cette année-là. */
        private long nouveauxBeneficiaires;
        /** Entreprises ayant au moins un crédit mis en place cette année-là. */
        private long beneficiaires;
        private long nbCredits;
        private BigDecimal montantAccorde;
        private BigDecimal montantConsomme;
    }
}
