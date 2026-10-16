package mr.gov.finances.sgci;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import mr.gov.finances.sgci.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.mail.enabled=true",
        "app.mail.from=noreply@test.local",
        "app.mail.override-recipient=it-reset@test.local"
})
class PasswordResetMailIT {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private EmailService emailService;

    @Test
    void sendPasswordResetApproved_withOverride_sendsMail() throws Exception {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(session));

        emailService.sendPasswordResetApproved("user@test.local", "entreprise", "TempPass1234");

        // L'envoi est @Async : une attente fixe est une course, pas une synchronisation. Elle se
        // gagnait tant que la suite restait courte, et s'est mise à se perdre quand elle a grossi —
        // un échec qui ne dit rien du code testé et coûte une enquête à chaque fois.
        // timeout() interroge jusqu'à ce que l'envoi ait lieu, et rend la main dès qu'il a lieu :
        // déterministe, et plus rapide dans le cas normal.
        verify(mailSender, timeout(10_000).atLeastOnce()).send(any(MimeMessage.class));
    }
}
