package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.CategorieEntreprise;

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

    /** {@code true} si l'entreprise est étrangère : le NIF devient facultatif et {@link #registreCommerceEtranger} obligatoire. */
    @Column(nullable = false)
    @Builder.Default
    private boolean entrepriseEtrangere = false;

    /** Numéro de registre de commerce étranger (obligatoire pour une entreprise étrangère sans NIF local). */
    private String registreCommerceEtranger;

    /** PME ou grande entreprise ; {@code NON_RENSEIGNEE} tant que l'administration ne l'a pas saisie. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    @Builder.Default
    private CategorieEntreprise categorie = CategorieEntreprise.NON_RENSEIGNEE;

    /** Secteur d'activité, choisi dans le référentiel ({@code activite} reste le libellé libre). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secteur_activite_id")
    private SecteurActivite secteur;

    @OneToMany(mappedBy = "entreprise", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CertificatCredit> certificats = new ArrayList<>();

    @OneToMany(mappedBy = "entreprise", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UtilisationCredit> utilisations = new ArrayList<>();
}
