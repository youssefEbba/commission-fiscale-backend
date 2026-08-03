package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Groupement d'entreprises : ensemble de membres avec un chef de file.
 * Le NIF du groupement n'est jamais stocké : il est toujours dérivé du chef de file.
 * Le groupement est géré comme une entreprise (CRUD) et peut porter une demande de correction.
 */
@Entity
@Table(name = "groupement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Groupement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String raisonSociale;

    private String nomCommercial;

    private String adresse;

    @Column(length = 2000)
    private String autre;

    private String situationFiscale;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    /** Chef de file — doit appartenir à {@link #membres}. Son NIF est le NIF affiché du groupement. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chef_de_file_id", nullable = false)
    private Entreprise chefDeFile;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "groupement_membre",
            joinColumns = @JoinColumn(name = "groupement_id"),
            inverseJoinColumns = @JoinColumn(name = "entreprise_id")
    )
    @Builder.Default
    private Set<Entreprise> membres = new HashSet<>();

    private Instant dateCreation;
    private Instant dateModification;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        dateCreation = now;
        dateModification = now;
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = Instant.now();
    }
}
