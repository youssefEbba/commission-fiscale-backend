package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@DiscriminatorValue("DOUANIER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UtilisationDouaniere extends UtilisationCredit {

    private String numeroDeclaration;
    private String numeroBulletin;
    private Instant dateDeclaration;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantDroits;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantTVA;

    private Boolean enregistreeSYDONIA;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeCordonAvant;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeCordonApres;
}
