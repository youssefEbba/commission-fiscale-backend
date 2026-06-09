package mr.gov.finances.sgci;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.WorkflowEventCode;
import mr.gov.finances.sgci.notification.WorkflowNotificationContext;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.NotificationRepository;
import mr.gov.finances.sgci.service.EmailService;
import mr.gov.finances.sgci.service.WorkflowNotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.mail.enabled=true",
        "app.mail.from=noreply@test.local",
        "app.mail.override-recipient=it-test@esen.tn"
})
class WorkflowNotificationMailIT {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private EmailService emailService;

    @Autowired
    private WorkflowNotificationDispatcher dispatcher;

    @Autowired
    private DemandeCorrectionRepository demandeCorrectionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void sendWorkflowNotification_withOverride_sendsOneMail() throws Exception {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props);
        MimeMessage mime = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mime);

        emailService.sendWorkflowNotification(
                "ignored@example.com",
                "[SGCI] Test correction",
                WorkflowEventCode.CORRECTION_SOUMISE.name(),
                "DC-TEST",
                "Demande de correction soumise",
                Map.of("newStatus", "RECUE"));

        Thread.sleep(1500);
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void dispatchCorrectionSoumise_createsInAppNotificationAndMail() throws Exception {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(session));

        DemandeCorrection demande = demandeCorrectionRepository.findAll().stream().findFirst().orElse(null);
        assertThat(demande).isNotNull();

        long before = notificationRepository.count();

        dispatcher.dispatch(WorkflowEventCode.CORRECTION_SOUMISE, WorkflowNotificationContext.builder()
                .entityType("DemandeCorrection")
                .entityId(demande.getId())
                .dossierLabel(demande.getNumero())
                .newStatus("RECUE")
                .entrepriseId(demande.getEntreprise() != null ? demande.getEntreprise().getId() : null)
                .autoriteContractanteId(demande.getAutoriteContractante() != null
                        ? demande.getAutoriteContractante().getId() : null)
                .roleRecipients(List.of(Role.DGTCP, Role.DGD))
                .build());

        assertThat(notificationRepository.count()).isGreaterThan(before);

        Thread.sleep(1500);
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }
}
