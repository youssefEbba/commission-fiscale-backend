package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "entreprise")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entreprise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String raisonSociale;

    /** Dénomination usuelle (enseigne) distincte de la raison sociale. */
    private String nomCommercial;

    /** Secteur ou nature d’activité. */
    private String activite;

    /** Informations complémentaires libres. */
    @Column(length = 2000)
    private String autre;

    @Column(unique = true)
    private String nif;

    private String adresse;

    private String situationFiscale;

    @OneToMany(mappedBy = "entreprise", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CertificatCredit> certificats = new ArrayList<>();

    @OneToMany(mappedBy = "entreprise", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UtilisationCredit> utilisations = new ArrayList<>();
}
