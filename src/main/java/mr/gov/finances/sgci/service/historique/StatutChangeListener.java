package mr.gov.finances.sgci.service.historique;

import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.ClotureCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.HistoriqueStatut;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.ReferentielProjet;
import mr.gov.finances.sgci.domain.entity.SousTraitance;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.security.ActeurCourant;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Détecte tout changement de statut, quel que soit le service qui l'a provoqué.
 *
 * <p><b>Pourquoi un écouteur plutôt qu'un appel dans chaque service.</b> Le dépôt compte 26 appels
 * au validateur de transition, mais 68 écritures de statut réparties sur 19 services. Les 42
 * restantes incluent {@code ConventionService}, {@code MarcheService}, {@code SousTraitanceService},
 * {@code ReferentielProjetService} — précisément les objets dont le suivi intéresse la Commission.
 * Et le dépôt ne contient aucun {@code @Modifying}, aucun {@code update … set statut} natif : tout
 * passe par une entité gérée par une session, donc cet écouteur n'a pas d'angle mort. C'est
 * vérifiable, et ce n'est pas un argument de style.
 *
 * <p><b>Ce que l'écouteur ne fait pas : écrire.</b> Il est appelé pendant le vidage de la session,
 * alors que la file d'actions se purge ; y insérer une entité est le moyen le plus court d'obtenir
 * un comportement erratique. Il détecte, tamponne, et laisse une synchronisation de transaction
 * écrire <em>après</em> le commit — de sorte qu'une transaction annulée ne laisse aucune ligne, et
 * qu'un défaut de ce mécanisme neuf ne puisse pas faire échouer un visa.
 *
 * <p>Il ne détient <b>aucun bean Spring</b> : seulement un fournisseur, résolu au moment d'écrire.
 * L'injecter directement reviendrait à faire dépendre la création de l'{@code EntityManagerFactory}
 * de beans qu'elle sert à construire.
 */
@Slf4j
public class StatutChangeListener implements PostUpdateEventListener, PostInsertEventListener {

    /** Le nom de la propriété surveillée, identique sur toutes les entités concernées. */
    private static final String PROPRIETE = "statut";

    /**
     * Les entités dont les transitions sont historisées.
     *
     * <p>Liste blanche explicite, et non « toute entité portant un statut » :
     * {@code DemandeResetPassword}, {@code DemandeExplication} et {@code DemandeRattachement} en
     * portent un aussi, sans que leur chronologie intéresse le reporting.
     */
    private static final List<Class<?>> SURVEILLEES = List.of(
            DemandeCorrection.class, CertificatCredit.class, UtilisationCredit.class,
            TransfertCredit.class, SousTraitance.class, ClotureCredit.class,
            Convention.class, Marche.class, ReferentielProjet.class);

    private static final Object CLE_TAMPON = new Object();

    private final Supplier<HistoriqueStatutWriter> writer;

    /** L'index de la propriété par persister, résolu une fois. */
    private final Map<String, Integer> indexParPersister = new ConcurrentHashMap<>();

    public StatutChangeListener(Supplier<HistoriqueStatutWriter> writer) {
        this.writer = writer;
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Class<?> racine = racineSurveillee(event.getEntity());
        if (racine == null) {
            return;
        }
        int index = indexDeLaPropriete(event.getPersister());
        if (index < 0) {
            return;
        }
        Object[] etatAvant = event.getOldState();
        String apres = texte(event.getState()[index]);
        if (apres == null) {
            return;
        }
        // L'état chargé manque sur une entité détachée — import d'archive, migration de démarrage.
        // On écrit alors la ligne sans provenance plutôt que de la perdre.
        String avant = etatAvant != null ? texte(etatAvant[index]) : null;
        if (etatAvant != null && Objects.equals(avant, apres)) {
            // Une mise à jour qui ne touche pas au statut déclenche aussi cet écouteur.
            return;
        }
        tamponner(ligne(racine, event.getId(), avant, apres, "UPDATE"));
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        // Obligatoire, pas optionnel : sans la première étape, « depuis combien de temps dans ce
        // statut » est faux pour tout dossier qui n'a jamais été modifié depuis sa création.
        Class<?> racine = racineSurveillee(event.getEntity());
        if (racine == null) {
            return;
        }
        int index = indexDeLaPropriete(event.getPersister());
        if (index < 0) {
            return;
        }
        String apres = texte(event.getState()[index]);
        if (apres == null) {
            return;
        }
        tamponner(ligne(racine, event.getId(), null, apres, "INSERT"));
    }

    /**
     * Hibernate demande si l'écouteur doit être rejoué après le commit. Non : la détection se fait
     * pendant le vidage, et c'est notre propre synchronisation qui porte l'écriture.
     */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    // ── détection ──────────────────────────────────────────────────────────────────────────────

    /**
     * La classe surveillée dont l'entité relève, ou {@code null}.
     *
     * <p>Comparaison par assignabilité et non par égalité : {@code UtilisationCredit} est en
     * {@code SINGLE_TABLE}, donc l'entité reçue est une {@code UtilisationDouaniere} ou une
     * {@code UtilisationTVAInterieure}. C'est le nom de la <em>racine</em> qui est enregistré, pour
     * que le reporting puisse regrouper les utilisations sans connaître leurs sous-classes.
     */
    private Class<?> racineSurveillee(Object entity) {
        if (entity == null) {
            return null;
        }
        for (Class<?> surveillee : SURVEILLEES) {
            if (surveillee.isAssignableFrom(entity.getClass())) {
                return surveillee;
            }
        }
        return null;
    }

    /**
     * L'index de {@code statut} dans le tableau d'état, mis en cache par persister.
     *
     * <p>Résolu par {@code getPropertyNames()} et non par le métamodèle interne de Hibernate, dont
     * l'API a changé entre versions mineures de la 6.
     */
    private int indexDeLaPropriete(EntityPersister persister) {
        if (persister == null) {
            return -1;
        }
        return indexParPersister.computeIfAbsent(persister.getEntityName(), nom -> {
            String[] proprietes = persister.getPropertyNames();
            for (int i = 0; i < proprietes.length; i++) {
                if (PROPRIETE.equals(proprietes[i])) {
                    return i;
                }
            }
            return -1;
        });
    }

    private static String texte(Object valeur) {
        return valeur != null ? valeur.toString() : null;
    }

    private HistoriqueStatut ligne(Class<?> racine, Object id, String avant, String apres, String origine) {
        ActeurCourant.Acteur acteur = ActeurCourant.lire();
        return HistoriqueStatut.builder()
                .entityType(racine.getSimpleName())
                .entityId(id instanceof Number n ? n.longValue() : null)
                .statutAvant(avant)
                .statutApres(apres)
                .dateTransition(Instant.now())
                .utilisateurId(acteur.userId())
                .username(acteur.username())
                .roleActeur(acteur.role() != null ? acteur.role().name() : null)
                .origine(origine)
                .build();
    }

    // ── écriture, après le commit ──────────────────────────────────────────────────────────────

    private void tamponner(HistoriqueStatut ligne) {
        if (ligne.getEntityId() == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Hors transaction : les migrations au démarrage, par exemple. On écrit sur-le-champ.
            ecrire(List.of(ligne));
            return;
        }
        @SuppressWarnings("unchecked")
        List<HistoriqueStatut> tampon =
                (List<HistoriqueStatut>) TransactionSynchronizationManager.getResource(CLE_TAMPON);
        if (tampon == null) {
            tampon = new ArrayList<>();
            TransactionSynchronizationManager.bindResource(CLE_TAMPON, tampon);
            final List<HistoriqueStatut> aEcrire = tampon;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    // afterCompletion et non afterCommit : le tampon doit être délié dans tous les
                    // cas, et une transaction annulée ne doit laisser aucune ligne.
                    TransactionSynchronizationManager.unbindResourceIfPossible(CLE_TAMPON);
                    if (status == STATUS_COMMITTED) {
                        ecrire(aEcrire);
                    }
                }
            });
        }
        tampon.add(ligne);
    }

    private void ecrire(List<HistoriqueStatut> lignes) {
        try {
            writer.get().ecrire(lignes);
        } catch (Exception e) {
            log.warn("Historisation des statuts indisponible : {}", e.getMessage());
        }
    }
}
