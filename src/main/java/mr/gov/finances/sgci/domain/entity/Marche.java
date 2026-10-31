package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.domain.enums.TypeMarche;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "marche")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Marche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String numeroMarche;

    /** Référence lisible standardisée (ex. DM-001-01/2025). */
    @Column(unique = true)
    private String reference;

    /** Libellé descriptif (affichage, recherche). */
    @Column(length = 500)
    private String intitule;

    private LocalDate dateSignature;

    /** Montant HT du contrat (colonne historique {@code montant_contrat_ttc} conservée en base). */
    @Column(name = "montant_contrat_ttc", nullable = false, precision = 18, scale = 2)
    private BigDecimal montantContratHt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutMarche statut;

    /**
     * La nature de la prestation : fourniture, prestation de services ou travaux.
     *
     * <p>Nullable : les marchés déjà enregistrés n'en portent pas, et rien ne permet de le déduire
     * de leur intitulé. Les forcer à une valeur par défaut fabriquerait une donnée fausse qui se
     * retrouverait ensuite dans les états de la Commission.
     *
     * <p>Sa colonne est inscrite au registre de {@code ColonnesTexteSchemaMigration}, qui la
     * convertit en VARCHAR au démarrage — voir {@link TypeMarche}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_marche", length = 32)
    private TypeMarche typeMarche;

    /**
     * La convention principale du marché. Un marché peut en avoir plusieurs (décision de l'équipe,
     * octobre 2026) : {@link #conventions} les porte toutes, celle-ci comprise.
     */
    @ManyToOne
    @JoinColumn(name = "convention_id")
    private Convention convention;

    /** Toutes les conventions du marché, la principale comprise (table {@code marche_convention}). */
    @ManyToMany
    @JoinTable(name = "marche_convention",
            joinColumns = @JoinColumn(name = "marche_id"),
            inverseJoinColumns = @JoinColumn(name = "convention_id"))
    @Builder.Default
    private Set<Convention> conventions = new LinkedHashSet<>();

    /**
     * L'autorité contractante qui porte le marché.
     *
     * <p>Elle se lisait jusqu'ici sur la convention. Depuis qu'une convention peut relier plusieurs
     * autorités, rien ne disait plus à qui appartient le marché, alors que les contrôles d'accès et
     * les délégués en dépendent. Nullable : les marchés existants la reçoivent de leur convention, au
     * démarrage ({@code ConventionMultiLiensMigration}), les nouveaux à leur création.
     */
    @ManyToOne
    @JoinColumn(name = "autorite_contractante_id")
    private AutoriteContractante autoriteContractante;

    @OneToOne
    @JoinColumn(name = "demande_correction_id", unique = true)
    private DemandeCorrection demandeCorrection;

    @Builder.Default
    @OneToMany(mappedBy = "marche", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MarcheDelegue> delegues = new HashSet<>();

    /** L'autorité qui porte le marché ; à défaut, celle de sa convention principale. */
    public AutoriteContractante autoriteEffective() {
        if (autoriteContractante != null) {
            return autoriteContractante;
        }
        return convention != null ? convention.getAutoriteContractante() : null;
    }

    /** Toutes les conventions du marché, la principale en tête, sans doublon. */
    public Set<Convention> conventionsLiees() {
        Map<Object, Convention> parId = new LinkedHashMap<>();
        if (convention != null) {
            parId.put(cle(convention), convention);
        }
        if (conventions != null) {
            conventions.forEach(c -> parId.putIfAbsent(cle(c), c));
        }
        return new LinkedHashSet<>(parId.values());
    }

    /** Vrai si la convention fait partie de celles du marché. */
    public boolean relieA(Long conventionId) {
        return conventionId != null && conventionsLiees().stream().anyMatch(c -> conventionId.equals(c.getId()));
    }

    private static Object cle(Convention c) {
        return c.getId() != null ? c.getId() : c;
    }

    /**
     * Garde les liens cohérents quel que soit le chemin de création (service, seed, import
     * d'archives, fixtures de test) : la convention principale figure parmi les conventions, et le
     * marché porte une autorité.
     */
    @PrePersist
    protected void synchroniserLiens() {
        if (convention != null) {
            if (conventions == null) {
                conventions = new LinkedHashSet<>();
            }
            Object principale = cle(convention);
            if (conventions.stream().noneMatch(c -> cle(c).equals(principale))) {
                conventions.add(convention);
            }
        }
        if (autoriteContractante == null && convention != null) {
            autoriteContractante = convention.getAutoriteContractante();
        }
    }
}
