package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.SensDocument;
import mr.gov.finances.sgci.domain.enums.TypeFichierAutorise;

import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "document_requirement",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_doc_req_process_code", columnNames = {"processus", "code_document"})
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

    @Column(name = "code_document", nullable = false, length = 64)
    private String codeDocument;

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

    /**
     * Pièce fournie par le déposant, ou produite par la Commission et remise en fin de processus.
     *
     * <p><b>Nullable à dessein</b>, là où un défaut {@code ENTREE} en base aurait paru plus propre.
     * {@code null} dit « personne n'a tranché », ce qu'une valeur par défaut ne sait pas dire — et
     * c'est cette distinction qui permet au seed de renseigner les sorties connues sans jamais
     * écraser un sens posé depuis l'écran de paramétrage. Même raison que pour {@code description}
     * et {@code ordreAffichage}, que {@code seedDocReq} ne touche que s'ils sont vides.
     *
     * <p>Lu partout comme {@code ENTREE} : une pièce non classée reste visible.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "sens", length = 16)
    private SensDocument sens;

    /** Le sens effectif : {@code ENTREE} tant que rien n'a été tranché. */
    public SensDocument sensEffectif() {
        return sens != null ? sens : SensDocument.ENTREE;
    }
}
