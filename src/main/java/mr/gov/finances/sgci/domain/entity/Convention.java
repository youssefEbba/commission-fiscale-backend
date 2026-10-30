package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.StatutConvention;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Entity
@Table(name = "convention")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Convention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference;

    /** Référence projet (cadrage interne AC / bailleur), distincte de {@link #reference}. */
    @Column(length = 255)
    private String projectReference;

    @Column(nullable = false)
    private String intitule;

    /**
     * Le bailleur principal. Une convention peut en avoir plusieurs (décision de l'équipe, octobre
     * 2026) : {@link #bailleurs} les porte tous, celui-ci compris.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bailleur_id")
    private Bailleur bailleur;

    /** Tous les bailleurs, le principal compris (table {@code convention_bailleur}). */
    @ManyToMany
    @JoinTable(name = "convention_bailleur",
            joinColumns = @JoinColumn(name = "convention_id"),
            inverseJoinColumns = @JoinColumn(name = "bailleur_id"))
    @Builder.Default
    private Set<Bailleur> bailleurs = new LinkedHashSet<>();

    private LocalDate dateSignature;
    private LocalDate dateFin;

    private BigDecimal montantDevise;
    private BigDecimal montantMru;
    private String deviseOrigine;
    private BigDecimal tauxChange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutConvention statut = StatutConvention.EN_ATTENTE;

    /**
     * L'autorité titulaire, dite principale. Une convention peut être reliée à plusieurs autorités
     * (décision de l'équipe, octobre 2026) : {@link #autorites} les porte toutes, celle-ci comprise.
     * Seuls le Président et l'administrateur relient ou délient une autorité.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_contractante_id", nullable = false)
    private AutoriteContractante autoriteContractante;

    /** Toutes les autorités reliées, la principale comprise (table {@code convention_autorite}). */
    @ManyToMany
    @JoinTable(name = "convention_autorite",
            joinColumns = @JoinColumn(name = "convention_id"),
            inverseJoinColumns = @JoinColumn(name = "autorite_contractante_id"))
    @Builder.Default
    private Set<AutoriteContractante> autorites = new LinkedHashSet<>();

    /** Autorité dont l’utilisateur a créé l’enregistrement (souvent identique à {@link #autoriteContractante}). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par_autorite_contractante_id")
    private AutoriteContractante creeParAutoriteContractante;

    /**
     * Disponibilité de la convention, distincte de {@link #statut} qui porte la validation.
     *
     * <p>Nullable à dessein : la colonne est postérieure aux conventions déjà en base, et une colonne
     * {@code NOT NULL} ajoutée sur une table peuplée les aurait toutes mises à {@code false}, donc
     * fermées d'un coup. {@code null} se lit « active » — voir
     * {@code ConventionDisponibilitePolicy} — et {@code ConventionActivationBackfillMigration}
     * régularise les lignes anciennes au démarrage.
     */
    @Column(name = "actif")
    @Builder.Default
    private Boolean actif = Boolean.TRUE;

    private Long valideParUserId;
    private Instant dateValidation;

    @Column(length = 1000)
    private String motifRejet;

    private Instant dateCreation;

    @OneToMany(mappedBy = "convention", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentConvention> documents = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        dateCreation = Instant.now();
        // Quel que soit le chemin de création (service, seed, import, fixture), la principale
        // figure parmi les autorités, et le bailleur principal parmi les bailleurs.
        if (autoriteContractante != null) {
            autorites = ajouterSiAbsent(autorites, autoriteContractante, AutoriteContractante::getId);
        }
        if (bailleur != null) {
            bailleurs = ajouterSiAbsent(bailleurs, bailleur, Bailleur::getId);
        }
    }

    /** Toutes les autorités reliées, la principale en tête, sans doublon. */
    public Set<AutoriteContractante> autoritesLiees() {
        return union(autoriteContractante, autorites, AutoriteContractante::getId);
    }

    /** Tous les bailleurs, le principal en tête, sans doublon. */
    public Set<Bailleur> bailleursLies() {
        return union(bailleur, bailleurs, Bailleur::getId);
    }

    /** Vrai si l'autorité est reliée à la convention (l'avoir créée n'y suffit pas). */
    public boolean relieeA(Long autoriteId) {
        return autoriteId != null && autoritesLiees().stream().anyMatch(a -> autoriteId.equals(a.getId()));
    }

    /** Vrai si l'autorité est reliée à la convention ou l'a créée : ce sont celles qui la voient. */
    public boolean concerne(Long autoriteId) {
        return relieeA(autoriteId) || (autoriteId != null && creeParAutoriteContractante != null
                && autoriteId.equals(creeParAutoriteContractante.getId()));
    }

    private static <T> Set<T> union(T principal, Set<T> autres, Function<T, Long> id) {
        Map<Object, T> parId = new LinkedHashMap<>();
        if (principal != null) {
            parId.put(cle(principal, id), principal);
        }
        if (autres != null) {
            autres.forEach(a -> parId.putIfAbsent(cle(a, id), a));
        }
        return new LinkedHashSet<>(parId.values());
    }

    private static <T> Set<T> ajouterSiAbsent(Set<T> ensemble, T element, Function<T, Long> id) {
        Set<T> s = ensemble != null ? ensemble : new LinkedHashSet<>();
        Object cle = cle(element, id);
        if (s.stream().noneMatch(e -> Objects.equals(cle(e, id), cle))) {
            s.add(element);
        }
        return s;
    }

    /** L'identifiant, ou l'objet lui-même tant qu'il n'est pas enregistré. */
    private static <T> Object cle(T e, Function<T, Long> id) {
        Long v = id.apply(e);
        return v != null ? v : e;
    }
}
