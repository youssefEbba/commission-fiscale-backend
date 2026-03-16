package mr.gov.finances.sgci.web.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneImportationDto {
    private Long id;
    private String designation;
    private String unite;
    private double quantite;
    private double prixUnitaire;
    private String nomenclature;
    private double tauxDD;
    private double tauxRS;
    private double tauxPSC;
    private double tauxTVA;
    private Double valeurDouane;
    private Double dd;
    private Double rs;
    private Double psc;
    private Double baseTVA;
    private Double tvaDouane;
    private Double totalTaxes;
}
