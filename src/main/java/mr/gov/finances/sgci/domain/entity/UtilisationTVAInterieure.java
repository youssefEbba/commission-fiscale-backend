package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.TypeAchat;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@DiscriminatorValue("TVA_INTERIEURE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UtilisationTVAInterieure extends UtilisationCredit {

    @Enumerated(EnumType.STRING)
    private TypeAchat typeAchat;
    private String numeroFacture;
    private Instant dateFacture;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantTVA;

    private String numeroDecompte;
}
