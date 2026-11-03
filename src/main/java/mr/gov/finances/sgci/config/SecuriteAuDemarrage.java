package mr.gov.finances.sgci.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Contrôle des secrets au démarrage, avant que le serveur ne réponde à quiconque.
 *
 * <p>En production, un secret absent ou déjà publié arrête le démarrage, avec la liste des
 * variables d'environnement à définir (voir {@link ExigencesProduction}).
 *
 * <p>Hors production, une clé de signature absente est remplacée par une clé aléatoire, valable le
 * temps de la session : le poste de développement démarre sans configuration, et aucune clé n'est
 * écrite dans le code. Les jetons émis ne survivent pas à un redémarrage.
 */
@Slf4j
@Component
public class SecuriteAuDemarrage implements InitializingBean {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Environment environment;
    private final JwtProperties jwtProperties;

    public SecuriteAuDemarrage(Environment environment, JwtProperties jwtProperties) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> manquements = ExigencesProduction.manquements(environment);
        if (!manquements.isEmpty()) {
            throw new IllegalStateException("Démarrage en production refusé, secrets à définir dans l'environnement :\n - "
                    + String.join("\n - ", manquements));
        }
        String cle = jwtProperties.getSecret();
        if (cle == null || cle.isBlank()) {
            byte[] octets = new byte[48];
            SECURE_RANDOM.nextBytes(octets);
            jwtProperties.setSecret(Base64.getEncoder().encodeToString(octets));
            log.warn("APP_JWT_SECRET absent : clé de signature aléatoire pour cette session de développement "
                    + "(les jetons émis ne survivront pas au redémarrage)");
        }
    }
}
