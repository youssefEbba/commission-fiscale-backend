package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "modele_fiscal_ligne_importation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneImportation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modele_fiscal_id", nullable = false)
    private ModeleFiscal modeleFiscal;
}
