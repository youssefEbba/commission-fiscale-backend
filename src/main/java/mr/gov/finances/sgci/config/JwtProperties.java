package mr.gov.finances.sgci.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Aucune valeur dans le code : APP_JWT_SECRET, ou une clé de session hors production (SecuriteAuDemarrage). */
    private String secret;
    private long expirationMs = 28800000; // 8 heures : une journée de travail
    /** Durée des JWT d’impersonation commission relais (défaut 4 h). */
    private long relaisExpirationMs = 14400000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public long getRelaisExpirationMs() {
        return relaisExpirationMs;
    }

    public void setRelaisExpirationMs(long relaisExpirationMs) {
        this.relaisExpirationMs = relaisExpirationMs;
    }
}
