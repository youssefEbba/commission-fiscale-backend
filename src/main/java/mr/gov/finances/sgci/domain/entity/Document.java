package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.TypeDocument;

import java.time.Instant;

@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeDocument type;

    @Column(nullable = false)
    private String nomFichier;

    private String chemin;

    private Instant dateUpload;

    private Long taille;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id")
    private DemandeCorrection demandeCorrection;
}
