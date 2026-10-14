package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Où les dossiers sont arrêtés, et depuis combien de temps.
 *
 * <p><b>Une précision d'étiquette qui n'est pas une coquetterie.</b> « Âge dans le statut actuel »
 * n'est calculable pour aucun dossier aujourd'hui : rien n'enregistre quand le statut courant a été
 * pris, {@code dateModification} est réécrite par chaque mise à jour — statut ou non — et
 * {@code CertificatCredit} n'a même pas de date de création. Ce qui est livré est donc
 * <b>l'âge depuis la dernière décision enregistrée</b>, et le champ comme la colonne doivent le dire.
 * Le véritable âge dans le statut ne devient calculable que pour les transitions postérieures à la
 * mise en service de {@code historique_statut}.
 *
 * <p>Le chiffre le plus solide du bloc est ailleurs : {@link #rejetsTempOuverts}. Il se lit sur un
 * état explicite, {@code rejetTempStatus = OUVERT}, dont la valeur par défaut est {@code RESOLU} —
 * « ouvert » n'est donc jamais déduit d'un {@code null}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlocagesDto {

    /** Le seuil, en jours, au-delà duquel un dossier est compté comme immobile. */
    private int seuilImmobiliteJours;

    /**
     * Ce que mesure {@code joursDepuisDerniereDecision}, en clair.
     *
     * <p>Publié dans la réponse parce que ce libellé doit voyager jusqu'à l'en-tête de colonne et
     * jusqu'aux exports : une durée mal nommée se transforme en reproche adressé à la mauvaise
     * direction.
     */
    private String definitionAge;

    @Builder.Default
    private List<DossierImmobileDto> dossiersImmobiles = new ArrayList<>();

    private long nbDossiersImmobiles;

    /** Vrai si la liste a été tronquée ; le nombre total reste donné par {@link #nbDossiersImmobiles}. */
    private boolean dossiersImmobilesTronques;

    @Builder.Default
    private List<RejetTempOuvertDto> rejetsTempOuverts = new ArrayList<>();

    private long nbRejetsTempOuverts;

    private boolean rejetsTempOuvertsTronques;

    /** Ancienneté du plus vieux rejet temporaire encore ouvert, en jours. */
    private Long anciennetePlusVieuxRejetJours;

    @Builder.Default
    private List<DelaiDirectionDto> delaisParDirection = new ArrayList<>();

    private boolean filtersApplied;
}
