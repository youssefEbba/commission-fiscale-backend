package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "modele_fiscal_fiscalite_interieure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FiscaliteInterieure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double montantHT;
    private Double tauxTVA;
    private Double autresTaxes;

    private Double tvaCollectee;
    private Double tvaDeductible;
    private Double tvaNette;
    private Double creditInterieur;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modele_fiscal_id", nullable = false, unique = true)
    private ModeleFiscal modeleFiscal;
}
