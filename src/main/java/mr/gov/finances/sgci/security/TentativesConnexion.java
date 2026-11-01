package mr.gov.finances.sgci.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Échecs de connexion par identifiant : au cinquième échec, l'identifiant est bloqué quinze minutes,
 * même avec le bon mot de passe.
 *
 * <p>Sans cette limite, un mot de passe pouvait être essayé sans fin. Le blocage porte sur
 * l'identifiant visé, pas sur l'adresse de l'appelant : changer d'adresse ne relance pas les essais.
 * Le prix est qu'un tiers peut bloquer un compte quinze minutes ; c'est le compromis courant, et
 * le compte se débloque seul.
 *
 * <p>En mémoire : un serveur unique, et un redémarrage efface les compteurs, ce qui ne rouvre au
 * pire qu'une fenêtre de cinq essais.
 */
@Component
public class TentativesConnexion {

    static final int ECHECS_AVANT_BLOCAGE = 5;
    static final Duration DUREE_BLOCAGE = Duration.ofMinutes(15);
    /** Au-delà, on purge : borne la mémoire face à des identifiants inventés en masse. */
    private static final int MAX_ENTREES = 10_000;

    private final Map<String, Etat> etats = new ConcurrentHashMap<>();

    private record Etat(int echecs, Instant dernierEchec) {}

    /** Minutes de blocage restantes pour cet identifiant, 0 s'il peut se connecter. */
    public long minutesDeBlocage(String identifiant) {
        Etat e = etats.get(cle(identifiant));
        if (e == null || e.echecs() < ECHECS_AVANT_BLOCAGE) {
            return 0;
        }
        Instant fin = e.dernierEchec().plus(DUREE_BLOCAGE);
        Instant maintenant = Instant.now();
        if (!fin.isAfter(maintenant)) {
            etats.remove(cle(identifiant));
            return 0;
        }
        return Math.max(1, Duration.between(maintenant, fin).toMinutes() + 1);
    }

    public void echec(String identifiant) {
        Instant maintenant = Instant.now();
        if (etats.size() > MAX_ENTREES) {
            etats.entrySet().removeIf(en -> en.getValue().dernierEchec().plus(DUREE_BLOCAGE).isBefore(maintenant));
        }
        etats.merge(cle(identifiant), new Etat(1, maintenant),
                (ancien, nouveau) -> ancien.dernierEchec().plus(DUREE_BLOCAGE).isBefore(maintenant)
                        ? nouveau
                        : new Etat(ancien.echecs() + 1, maintenant));
    }

    public void succes(String identifiant) {
        etats.remove(cle(identifiant));
    }

    private static String cle(String identifiant) {
        return identifiant == null ? "" : identifiant.trim().toLowerCase(Locale.ROOT);
    }
}
