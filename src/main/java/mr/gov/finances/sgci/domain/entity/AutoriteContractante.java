package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "autorite_contractante")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoriteContractante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(unique = true)
    private String code;

    private String contact;

    /** Nom du ministère de tutelle (affichage en-têtes de demandes / PDF). */
    private String ministereTutelleNom;

    /** Code du ministère de tutelle. */
    private String ministereTutelleCode;

    @OneToMany(mappedBy = "autoriteContractante", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DemandeCorrection> demandes = new ArrayList<>();
}
