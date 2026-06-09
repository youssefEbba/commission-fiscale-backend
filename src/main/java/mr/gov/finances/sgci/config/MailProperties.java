package mr.gov.finances.sgci.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class MailProperties {

    /** Active l'envoi SMTP (désactivé en test par défaut). */
    private boolean enabled = false;

    private String from = "noreply@sgci.local";

    /** Phase test : tous les mails vont à cette adresse si renseignée. */
    private String overrideRecipient;

    private boolean async = true;
}
