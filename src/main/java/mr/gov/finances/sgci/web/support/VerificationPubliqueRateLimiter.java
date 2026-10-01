package mr.gov.finances.sgci.web.support;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limitation de débit par adresse IP sur la vérification publique.
 *
 * <p>L'endpoint est ouvert sans authentification et les numéros de documents sont séquentiels : un
 * balayage les énumérerait en quelques minutes. La réponse ne contient aucune donnée financière,
 * donc l'enjeu est modeste — mais rien ne justifie de laisser moissonner la liste des bénéficiaires.
 *
 * <p>Fenêtre glissante grossière, en mémoire, sans dépendance : le projet n'embarque pas de
 * limiteur, et en ajouter un pour ce seul besoin serait disproportionné. Conséquence assumée : la
 * limite est <b>par instance</b> et ne survit pas à un redémarrage. Derrière plusieurs instances ou
 * un répartiteur de charge, elle doit être reprise au niveau de la passerelle.
 */
@Component
public class VerificationPubliqueRateLimiter {

    private static final int MAX_PAR_FENETRE = 30;
    private static final Duration FENETRE = Duration.ofMinutes(1);
    /** Au-delà, on purge : borne la mémoire face à une distribution d'adresses. */
    private static final int MAX_ENTREES = 10_000;

    private final Map<String, Compteur> compteurs = new ConcurrentHashMap<>();

    private record Compteur(Instant debut, AtomicInteger appels) {}

    /** {@code true} si l'appel est accepté, {@code false} s'il dépasse le quota. */
    public boolean autorise(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        Instant maintenant = Instant.now();
        if (compteurs.size() > MAX_ENTREES) {
            compteurs.entrySet().removeIf(e -> estExpire(e.getValue(), maintenant));
        }
        Compteur compteur = compteurs.compute(ip, (cle, actuel) ->
                (actuel == null || estExpire(actuel, maintenant))
                        ? new Compteur(maintenant, new AtomicInteger())
                        : actuel);
        return compteur.appels().incrementAndGet() <= MAX_PAR_FENETRE;
    }

    private static boolean estExpire(Compteur c, Instant maintenant) {
        return c.debut().plus(FENETRE).isBefore(maintenant);
    }
}
