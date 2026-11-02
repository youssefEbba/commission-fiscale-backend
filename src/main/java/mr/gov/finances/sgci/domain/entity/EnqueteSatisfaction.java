package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mr.gov.finances.sgci.domain.enums.ProfilFeedback;
import mr.gov.finances.sgci.domain.enums.StatutEnquete;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Une enquête de satisfaction périodique, adressée à un ou plusieurs profils d'usagers.
 *
 * <p>Le caractère périodique tient à la relance : une enquête close se duplique sur la période
 * suivante, questions comprises, et garde le lien vers celle dont elle est issue pour comparer les
 * vagues entre elles.
 */
@Entity
@Table(name = "enquete_satisfaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnqueteSatisfaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titre;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    /** Les profils ciblés, séparés par des virgules (ex. « ENTREPRISE,DOUANES »). */
    @Column(nullable = false, length = 255)
    private String profilsCibles;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private StatutEnquete statut = StatutEnquete.BROUILLON;

    private Long creeParUtilisateurId;

    private Instant dateCreation;

    private Instant datePublication;

    /** L'enquête dont celle-ci est la relance, {@code null} pour une première vague. */
    private Long enqueteOrigineId;

    @OneToMany(mappedBy = "enquete", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    @Builder.Default
    private List<QuestionEnquete> questions = new ArrayList<>();

    public Set<ProfilFeedback> profils() {
        if (profilsCibles == null || profilsCibles.isBlank()) {
            return EnumSet.noneOf(ProfilFeedback.class);
        }
        return Arrays.stream(profilsCibles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ProfilFeedback::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProfilFeedback.class)));
    }

    public void definirProfils(Set<ProfilFeedback> profils) {
        this.profilsCibles = profils.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    /** Ouverte aux réponses : publiée, et la date du jour comprise entre le début et la fin. */
    public boolean estOuverte(LocalDate aujourdhui) {
        return statut == StatutEnquete.PUBLIEE
                && !aujourdhui.isBefore(dateDebut) && !aujourdhui.isAfter(dateFin);
    }
}
