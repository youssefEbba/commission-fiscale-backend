package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mr.gov.finances.sgci.domain.enums.StatutMarche;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "marche")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Marche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String numeroMarche;

    @Column(nullable = false)
    private LocalDate dateSignature;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal montantContratTtc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutMarche statut;

    @ManyToOne
    @JoinColumn(name = "convention_id")
    private Convention convention;

    @OneToOne
    @JoinColumn(name = "demande_correction_id", unique = true)
    private DemandeCorrection demandeCorrection;

    @Builder.Default
    @OneToMany(mappedBy = "marche", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MarcheDelegue> delegues = new HashSet<>();
}
