package mr.gov.finances.sgci.web.dto.feedback;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.CategorieRetour;
import mr.gov.finances.sgci.domain.enums.ProfilFeedback;
import mr.gov.finances.sgci.domain.enums.StatutEnquete;
import mr.gov.finances.sgci.domain.enums.TypeQuestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Les échanges du module feedback et satisfaction (cahier des charges, point 3). */
public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    /** Un avis spontané : note, sujet et commentaire. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RetourRequest {
        @NotNull(message = "La note est obligatoire")
        @Min(value = 1, message = "La note va de 1 à 5")
        @Max(value = 5, message = "La note va de 1 à 5")
        private Integer note;
        @NotNull(message = "Le sujet est obligatoire")
        private CategorieRetour categorie;
        private String processus;
        private String page;
        @Size(max = 2000, message = "Le commentaire est limité à 2000 caractères")
        private String commentaire;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RetourDto {
        private Long id;
        private ProfilFeedback profil;
        private Integer note;
        private CategorieRetour categorie;
        private String processus;
        private String page;
        private String commentaire;
        private Instant dateCreation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionDto {
        private Long id;
        private Integer ordre;
        @NotBlank(message = "Le libellé de la question est obligatoire")
        private String libelle;
        @NotNull(message = "Le type de la question est obligatoire")
        private TypeQuestion type;
        private CategorieRetour categorie;
        /** Pour une question à choix. */
        private List<String> options;
        private Boolean obligatoire;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnqueteRequest {
        @NotBlank(message = "Le titre est obligatoire")
        private String titre;
        private String description;
        @NotNull(message = "La date de début est obligatoire")
        private LocalDate dateDebut;
        @NotNull(message = "La date de fin est obligatoire")
        private LocalDate dateFin;
        @NotEmpty(message = "Au moins un profil doit être ciblé")
        private Set<ProfilFeedback> profilsCibles;
        @NotEmpty(message = "Une enquête comporte au moins une question")
        @Valid
        private List<QuestionDto> questions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnqueteDto {
        private Long id;
        private String titre;
        private String description;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private Set<ProfilFeedback> profilsCibles;
        private StatutEnquete statut;
        /** Publiée et dans ses dates. */
        private boolean ouverte;
        private Instant datePublication;
        private Long enqueteOrigineId;
        private List<QuestionDto> questions;
        private long nbReponses;
        /** Pour l'usager : a-t-il déjà répondu ? */
        private Boolean dejaRepondu;
    }

    /** La relance d'une enquête sur une nouvelle période. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RelanceRequest {
        @NotNull(message = "La date de début est obligatoire")
        private LocalDate dateDebut;
        @NotNull(message = "La date de fin est obligatoire")
        private LocalDate dateFin;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReponseQuestionRequest {
        @NotNull(message = "La question est obligatoire")
        private Long questionId;
        private Integer note;
        private String texte;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReponseEnqueteRequest {
        @NotEmpty(message = "Aucune réponse")
        @Valid
        private List<ReponseQuestionRequest> reponses;
    }

    /** Une moyenne de notes et sa distribution, pour un profil, un sujet ou une question. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Agregat {
        private String cle;
        private String libelle;
        private long nbNotes;
        private BigDecimal noteMoyenne;
        /** Nombre de notes 1, 2, 3, 4, 5 (indices 0 à 4). */
        private List<Long> distribution;
        /** Part des notes 4 et 5, en %. */
        private Double satisfaitsPct;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Mois {
        private String periode;
        private long nbNotes;
        private BigDecimal noteMoyenne;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResultatEnquete {
        private Long enqueteId;
        private String titre;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private StatutEnquete statut;
        private long nbReponses;
        /** Usagers actifs des profils ciblés : la base du taux de réponse. */
        private long nbCibles;
        private Double tauxReponsePct;
        private BigDecimal noteMoyenne;
        private List<Agregat> parQuestion;
        private List<Agregat> parProfil;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Commentaire {
        private ProfilFeedback profil;
        private CategorieRetour categorie;
        private Integer note;
        private String texte;
        /** « Avis spontané » ou le titre de l'enquête. */
        private String source;
        private Instant date;
    }

    /** Le tableau de bord consolidé de la satisfaction. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableauDeBord {
        private Instant from;
        private Instant to;
        private long nbAvis;
        private BigDecimal noteMoyenne;
        private Double satisfaitsPct;
        private List<Agregat> parProfil;
        private List<Agregat> parCategorie;
        private List<Agregat> parProcessus;
        private List<Mois> evolution;
        /** Sujets notés en moyenne 4 ou plus. */
        private List<Agregat> pointsForts;
        /** Sujets notés en moyenne 2,5 ou moins. */
        private List<Agregat> difficultes;
        private List<ResultatEnquete> enquetes;
        /** Les derniers commentaires, présentés avec le profil et sans le nom de la personne. */
        private List<Commentaire> commentaires;
    }
}
