package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Groupement d'entreprises. Le NIF affiché ({@link #nifAffiche}) est toujours celui du chef de file
 * — il n'est jamais saisi ni stocké sur le groupement.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupementDto {

    private Long id;

    @NotBlank(message = "La raison sociale du groupement est obligatoire")
    private String raisonSociale;

    private String nomCommercial;
    private String adresse;
    private String autre;
    private String situationFiscale;

    @Builder.Default
    private boolean actif = true;

    @NotNull(message = "Le chef de file est obligatoire")
    private Long chefDeFileId;

    /** Lecture seule — raison sociale du chef de file. */
    private String chefDeFileRaisonSociale;

    /**
     * Ids des entreprises membres. Doit contenir le chef de file.
     * Au moins 2 membres attendus (chef + partenaire).
     */
    @NotEmpty(message = "Le groupement doit comporter au moins un membre")
    @Builder.Default
    private List<Long> membreIds = new ArrayList<>();

    /** Membres enrichis (lecture seule) pour l'affichage. */
    @Builder.Default
    private List<EntrepriseDto> membres = new ArrayList<>();

    /**
     * NIF à afficher : toujours le NIF du chef de file.
     * Lecture seule — ne jamais envoyer en écriture.
     */
    private String nifAffiche;

    private Instant dateCreation;
    private Instant dateModification;
}
