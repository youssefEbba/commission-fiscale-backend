package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Une transition de statut, telle qu'elle s'est produite.
 *
 * <p>Rien n'enregistrait jusqu'ici les changements d'état. Les 55 {@code WorkflowEventCode} ne sont
 * consommés que par l'envoi de notifications et ne sont jamais écrits ; le {@code audit_log} range
 * l'état dans un JSON non indexé, sous une action {@code UPDATE} qui couvre indifféremment un visa
 * de direction et la correction d'une faute de frappe ; et {@code dateModification} est réécrite
 * par chaque mise à jour, qu'elle touche au statut ou non. Conséquence : « depuis combien de temps
 * ce dossier est-il arrêté là » n'avait pas de réponse.
 *
 * <p><b>Ce que cette table n'est pas.</b> Son écriture est au mieux-effort, comme celle de l'audit :
 * une défaillance y est journalisée et avalée, parce qu'un défaut de traçabilité ne doit jamais
 * faire échouer un visa. Ce n'est donc <strong>pas une trace probante</strong>. La trace probante
 * reste les quatre tables {@code Decision*}, qui portent une vraie clé étrangère vers
 * l'utilisateur.
 *
 * <p><b>Aucune colonne énumérée, et c'est structurel.</b> {@code statutAvant} et {@code statutApres}
 * accueillent les valeurs de huit énumérations différentes — demande, certificat, utilisation,
 * transfert, convention, marché, sous-traitance, référentiel projet — qu'aucun type Java ne couvre.
 * Et un {@code @Enumerated} serait rendu par Hibernate en colonne MySQL {@code enum(…)} native,
 * qu'un redémarrage n'élargit jamais : ajouter un statut casserait l'écriture en production sans
 * qu'aucun test ne le voie, puisque H2 recrée son schéma à chaque exécution.
 */
@Entity
@Table(name = "historique_statut", indexes = {
        // La chronologie d'un dossier, et la lecture « depuis quand dans ce statut ».
        @Index(name = "idx_hs_entite", columnList = "entityType,entityId,dateTransition"),
        @Index(name = "idx_hs_date", columnList = "dateTransition"),
        // « Combien de dossiers stationnent en X, et depuis quand. »
        @Index(name = "idx_hs_apres_date", columnList = "statutApres,dateTransition")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoriqueStatut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Même vocabulaire que {@code AuditLog.entityType} : le nom simple de la classe. */
    @Column(nullable = false, length = 64)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    /**
     * {@code null} à la création de l'objet, ou quand Hibernate n'avait pas l'état chargé.
     *
     * <p>Le second cas se produit sur une entité détachée — un import d'archive, une migration de
     * démarrage. La ligne est alors écrite quand même : savoir qu'une transition a eu lieu sans
     * savoir d'où elle venait vaut mieux que de ne rien savoir.
     */
    @Column(length = 48)
    private String statutAvant;

    @Column(nullable = false, length = 48)
    private String statutApres;

    @Column(nullable = false)
    private Instant dateTransition;

    /**
     * Clé étrangère volontairement absente.
     *
     * <p>L'écriture se produit sur le chemin du commit ; une contrainte d'intégrité y imposerait une
     * vérification d'existence, pour un gain nul puisque cette table n'est pas probante. Le dépôt a
     * déjà ce précédent sur les visas de demande. Le coût assumé : aucune jointure vers
     * {@code Utilisateur}.
     */
    private Long utilisateurId;

    @Column(nullable = false, length = 100)
    private String username;

    /** Le rôle effectif de l'acteur, en impersonation celui sous lequel il agissait. */
    @Column(length = 32)
    private String roleActeur;

    /** {@code INSERT} ou {@code UPDATE} — comment la ligne a été détectée. */
    @Column(nullable = false, length = 16)
    private String origine;

    @PrePersist
    protected void onCreate() {
        if (dateTransition == null) {
            dateTransition = Instant.now();
        }
    }
}
