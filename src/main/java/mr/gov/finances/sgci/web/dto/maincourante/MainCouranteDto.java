package mr.gov.finances.sgci.web.dto.maincourante;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ce qui attend l'utilisateur connecté, tous processus confondus.
 *
 * <p>Remplace le tableau de bord, et ne répond pas à la même question que le bloc « blocages » du
 * reporting : celui-ci dit <em>où le système est arrêté</em>, vu de haut ; la main courante dit
 * <em>ce que moi je dois faire maintenant</em>. Un dossier arrêté chez quelqu'un d'autre est un
 * blocage, pas une tâche.
 *
 * <p><b>Les processus couverts sont publiés, et ceux qui ne le sont pas aussi.</b> Une liste de
 * travail qui omet un processus en silence fait croire qu'il n'y a rien à y faire — c'est pire
 * qu'une liste vide, parce qu'elle inspire confiance. {@link #processusNonCouverts} nomme donc ce
 * qui manque, et l'écran doit l'afficher.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MainCouranteDto {

    @Builder.Default
    private List<LigneMainCouranteDto> lignes = new ArrayList<>();

    private long total;

    /** Le nombre de dossiers par processus, pour les onglets ou les pastilles. */
    @Builder.Default
    private Map<String, Long> parProcessus = new LinkedHashMap<>();

    @Builder.Default
    private List<String> processusCouverts = new ArrayList<>();

    /**
     * Les processus volontairement absents, avec la raison, en clair.
     *
     * <p>À afficher. Sans cette mention, un agent conclurait de l'absence d'une ligne qu'il n'a
     * rien à faire sur ce processus.
     */
    @Builder.Default
    private List<String> processusNonCouverts = new ArrayList<>();

    /** Ce que mesure {@code joursDAttente}, en clair, pour l'en-tête de colonne. */
    private String definitionAttente;

    /** Vrai si la liste a été tronquée ; {@link #total} reste le compte réel. */
    private boolean tronquee;
}
