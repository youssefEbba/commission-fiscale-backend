package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.TypeProjet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modele_fiscal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModeleFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String referenceDossier;

    @Enumerated(EnumType.STRING)
    private TypeProjet typeProjet;

    private Boolean afficherNomenclature;

    private Instant dateCreation;
    private Instant dateModification;

    @OneToMany(mappedBy = "modeleFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LigneImportation> importations = new ArrayList<>();

    @OneToOne(mappedBy = "modeleFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    private FiscaliteInterieure fiscaliteInterieure;

    @OneToOne(mappedBy = "modeleFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Recapitulatif recapitulatif;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id", nullable = false, unique = true)
    private DemandeCorrection demandeCorrection;
}
