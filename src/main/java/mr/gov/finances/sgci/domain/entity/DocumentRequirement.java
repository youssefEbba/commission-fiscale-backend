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

    /**
     * Désactivée : la pièce n'est plus demandée dans ce processus — ni proposée au dépôt, ni exigée,
     * ni réclamable par une demande de pièce. Les exemplaires déjà déposés restent consultables.
     * Réversible depuis l'écran de paramétrage.
     *
     * <p>Nullable comme {@code sens}, et pour la même raison : les lignes antérieures à la colonne la
     * portent à {@code null}, qui se lit « active ». La lire « désactivée » aurait fermé d'un coup
     * toutes les pièces existantes.
     */
    @Column(name = "actif")
    private Boolean actif;

    /**
     * Supprimée du paramétrage, sans que la ligne disparaisse.
     *
     * <p>Une suppression physique ne tenait pas, et faisait courir un risque. Elle ne tenait pas :
     * le seed rejoue à chaque démarrage et recrée toute pièce connue qui manque. Elle faisait courir
     * un risque : le sens d'une pièce vit sur cette ligne, et une pièce de sortie effacée serait
     * devenue un « code inconnu », lu comme une entrée — donc visible du déposant avant la fin du
     * processus. Marquée supprimée, la ligne est trouvée par le seed, qui ne la recrée pas, et son
     * sens continue de protéger les pièces déjà déposées.
     */
    @Column(name = "date_suppression")
    private java.time.Instant dateSuppression;

    public boolean estActif() {
        return !Boolean.FALSE.equals(actif);
    }

    public boolean estSupprime() {
        return dateSuppression != null;
    }

    /** Demandée dans ce processus : ni désactivée, ni supprimée. */
    public boolean estUtilisable() {
        return estActif() && !estSupprime();
    }
}
