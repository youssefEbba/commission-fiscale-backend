package mr.gov.finances.sgci.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.service.historique.HistoriqueStatutWriter;
import mr.gov.finances.sgci.service.historique.StatutChangeListener;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Branche l'historisation des statuts sur Hibernate, une fois le contexte entièrement construit.
 *
 * <p>L'enregistrement se fait sur {@code ApplicationReadyEvent} et non par un
 * {@code HibernatePropertiesCustomizer} : un écouteur déclaré à la construction de
 * l'{@code EntityManagerFactory} et dépendant de beans Spring produit le cycle classique, puisque
 * ces beans ont eux-mêmes besoin de l'{@code EntityManagerFactory}. Ici l'écouteur ne reçoit qu'un
 * fournisseur, et le contexte est prêt depuis longtemps quand il s'en sert.
 *
 * <p>Conséquence à connaître : les {@code ApplicationRunner} de démarrage — amorçage du jeu de
 * démonstration, migrations de schéma — s'exécutent <b>avant</b> cet événement, donc leurs
 * écritures ne sont pas historisées. C'est voulu : l'historique doit raconter l'activité des
 * utilisateurs, pas le remplissage initial de la base.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class HistoriqueStatutListenerConfig {

    private final EntityManagerFactory entityManagerFactory;
    private final HistoriqueStatutWriter writer;

    @EventListener(ApplicationReadyEvent.class)
    public void enregistrerLEcouteur() {
        try {
            SessionFactoryImplementor sessionFactory =
                    entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            EventListenerRegistry registry = sessionFactory.getServiceRegistry()
                    .requireService(EventListenerRegistry.class);

            StatutChangeListener ecouteur = new StatutChangeListener(() -> writer);
            registry.appendListeners(EventType.POST_UPDATE, ecouteur);
            registry.appendListeners(EventType.POST_INSERT, ecouteur);
            log.info("Historisation des transitions de statut active");
        } catch (Exception e) {
            // Le mode de panne de ce mécanisme est le silence : s'il ne s'enregistre pas, il faut
            // au moins que le journal le dise, plutôt qu'une table vide qu'on croit alimentée.
            log.warn("Historisation des transitions de statut NON enregistrée : {}", e.getMessage());
        }
    }
}
