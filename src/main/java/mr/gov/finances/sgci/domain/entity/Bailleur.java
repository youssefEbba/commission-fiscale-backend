package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.CategorieBailleur;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bailleur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bailleur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom;

    @Column(length = 1000)
    private String details;

    /** Nature du bailleur : fonds nationaux ou partenaire extérieur (multilatéral, bilatéral, privé). */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    @Builder.Default
    private CategorieBailleur categorie = CategorieBailleur.NON_RENSEIGNE;

    /** Plusieurs conventions peuvent être liées au même bailleur (côté propriétaire : {@link Convention#bailleur}). */
    @OneToMany(mappedBy = "bailleur")
    @Builder.Default
    private List<Convention> conventions = new ArrayList<>();
}
