package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemandeRattachement;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Demande, déposée sans compte, d'être rattaché à une entreprise ou à une autorité contractante.
 *
 * <p>Remplace l'inscription libre, qui créait d'un même geste le compte <em>et</em> l'entité. Ici rien
 * n'est créé au dépôt : la demande attend que le Président l'approuve, et c'est l'approbation qui fait
 * naître l'entité si elle manquait, puis le compte.
 *
 * <p>Le mot de passe choisi par le demandeur est conservé <strong>haché</strong> jusqu'à
 * l'approbation. Le garder en clair, même brièvement, aurait fait de cette table un gisement de mots
 * de passe en attente ; le demander à nouveau après l'approbation aurait obligé à un second échange
 * avec une personne qui n'a pas encore de compte.
 *
 * <p>Calquée sur {@link DemandeResetPassword}, dont elle reprend le triptyque
 * {@code statut} / {@code dateTraitement} / {@code traitePar} / {@code motifRefus}.
 */
@Entity
@Table(name = "demande_rattachement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeRattachement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Référence de suivi communiquée au demandeur, qui n'a pas de compte pour consulter son dossier. */
    @Column(unique = true, length = 32)
    private String reference;

    // ── le demandeur ───────────────────────────────────────────────────────────────────────────

    @Column(nullable = false, length = 64)
    private String username;

    /**
     * Hachage du mot de passe choisi, repris tel quel à la création du compte.
     *
     * <p>Jamais exposé par un DTO : le vérifier en relisant {@code DemandeRattachementDto}.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String nomComplet;

    @Column(nullable = false)
    private String email;

    private String telephone;

    /**
     * Qualité demandée, restreinte par le service aux rôles qu'une inscription peut viser.
     *
     * <p>L'ancienne inscription acceptait {@link Role} au complet, {@code PRESIDENT} et
     * {@code ADMIN_SI} compris. Le contrôle vit dans le service et non ici, pour qu'un dossier
     * historique au rôle devenu interdit reste lisible.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role_demande", nullable = false, length = 32)
    private Role roleDemande;

    // ── la cible ───────────────────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "type_entite", nullable = false, length = 32)
    private TypeEntiteRattachement typeEntite;

    /** Renseignée quand le demandeur a choisi une entité du référentiel. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id")
    private Entreprise entreprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_contractante_id")
    private AutoriteContractante autoriteContractante;

    /**
     * Descriptif de l'entité à créer, quand aucune n'a été choisie.
     *
     * <p>Champs plats plutôt qu'une entité détachée : une {@code Entreprise} non persistée aurait eu
     * à traverser la sérialisation et la validation du référentiel sans exister, et une entité
     * persistée aurait ramené le défaut que ce lot corrige — une entité créée avant toute validation.
     */
    @Column(name = "entite_nom", length = 255)
    private String entiteNom;

    @Column(name = "entite_sigle", length = 64)
    private String entiteSigle;

    @Column(name = "entite_nif", length = 64)
    private String entiteNif;

    @Column(name = "entite_adresse", length = 255)
    private String entiteAdresse;

    @Column(name = "entite_activite", length = 255)
    private String entiteActivite;

    @Column(name = "entite_ministere_tutelle", length = 255)
    private String entiteMinistereTutelle;

    // ── l'instruction ──────────────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatutDemandeRattachement statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    @Column(name = "date_traitement")
    private Instant dateTraitement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "traite_par_id")
    private Utilisateur traitePar;

    @Column(name = "motif_refus", length = 1000)
    private String motifRefus;

    /** Compte créé à l'approbation — la trace de ce que la décision a produit. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_cree_id")
    private Utilisateur utilisateurCree;

    @OneToMany(mappedBy = "demandeRattachement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentDemandeRattachement> documents = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (dateCreation == null) {
            dateCreation = Instant.now();
        }
        if (statut == null) {
            statut = StatutDemandeRattachement.EN_ATTENTE;
        }
    }

    /** Vrai si la demande porte une entité à créer plutôt qu'une entité choisie. */
    public boolean porteUneEntiteNouvelle() {
        return entreprise == null && autoriteContractante == null;
    }
}
