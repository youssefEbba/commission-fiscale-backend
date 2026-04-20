package mr.gov.finances.sgci.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "X7mD!r8KpE6@Z5YVQ#s1A4dFJ0Uo$B9%2H^tWCLN*eG";
    private long expirationMs = 86400000; // 24 heures
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
