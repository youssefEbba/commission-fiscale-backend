package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "code_document", nullable = false, length = 64)
    private String codeDocument;

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
