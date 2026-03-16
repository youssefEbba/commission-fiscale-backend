package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "certificat_credit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String numero;

    private Instant dateEmission;
    private Instant dateValidite;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantCordon;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantTVAInterieure;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeCordon;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeTVA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutCertificat statut = StatutCertificat.DEMANDE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lettre_correction_id")
    private LettreCorrection lettreCorrection;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id", unique = true)
    private DemandeCorrection demandeCorrection;

    @OneToMany(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Item> items = new ArrayList<>();

    @OneToMany(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UtilisationCredit> utilisations = new ArrayList<>();

    @OneToMany(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Avenant> avenants = new ArrayList<>();

    @OneToOne(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    private TransfertCredit transfertCredit;

    @OneToOne(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    private SousTraitance sousTraitance;

    @OneToOne(mappedBy = "certificatCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    private ClotureCredit clotureCredit;
}
