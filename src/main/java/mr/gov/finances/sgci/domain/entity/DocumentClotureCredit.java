package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.TypeDocument;

import java.time.Instant;

@Entity
@Table(name = "document_cloture_credit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentClotureCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeDocument type;

    @Column(nullable = false)
    private String nomFichier;

    @Column(nullable = false)
    private String chemin;

    private Instant dateUpload;

    private Long taille;

    private Integer version;

    @Builder.Default
    private Boolean actif = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloture_credit_id", nullable = false)
    private ClotureCredit clotureCredit;
}
