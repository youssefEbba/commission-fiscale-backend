package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;

import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "document_requirement",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_doc_req_process_type", columnNames = {"processus", "type_document"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessusDocument processus;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false)
    private TypeDocument typeDocument;

    @Column(nullable = false)
    @Builder.Default
    private Boolean obligatoire = Boolean.TRUE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "document_requirement_type_fichier",
            joinColumns = @JoinColumn(name = "document_requirement_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "type_fichier", nullable = false)
    @Builder.Default
    private Set<TypeFichierAutorise> typesAutorises = EnumSet.noneOf(TypeFichierAutorise.class);

    @Column(length = 1000)
    private String description;

    @Column(name = "ordre_affichage")
    private Integer ordreAffichage;
}
