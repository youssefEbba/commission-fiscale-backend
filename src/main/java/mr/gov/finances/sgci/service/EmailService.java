package mr.gov.finances.sgci.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.config.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final SpringTemplateEngine templateEngine;

    @Async
    public void sendWorkflowNotification(String toEmail, String subject, String eventCode,
                                         String dossierLabel, String bodyMessage, Map<String, Object> variables) {
        if (!mailProperties.isEnabled()) {
            return;
        }
        String recipient = mailProperties.getOverrideRecipient();
        if (recipient == null || recipient.isBlank()) {
            if (toEmail == null || toEmail.isBlank()) {
                log.debug("E-mail ignoré (adresse vide) pour l'événement {}", eventCode);
                return;
            }
            recipient = toEmail;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("eventCode", eventCode);
            ctx.setVariable("dossierLabel", dossierLabel != null ? dossierLabel : "");
            ctx.setVariable("message", bodyMessage != null ? bodyMessage : "");
            if (variables != null) {
                variables.forEach(ctx::setVariable);
            }
            String template = resolveTemplate(eventCode);
            String html = templateEngine.process(template, ctx);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.getFrom());
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mime);
            log.info("E-mail envoyé à {} (événement {})", recipient, eventCode);
        } catch (MessagingException | RuntimeException e) {
            // Inclut org.springframework.mail.MailException (SMTP indisponible/auth) :
            // un échec d'envoi ne doit jamais faire échouer l'opération métier.
            log.warn("Échec envoi e-mail à {} (événement {}): {}", recipient, eventCode, e.getMessage());
        }
    }

    @Async
    public void sendToUsers(List<Long> userIds, WorkflowRecipientResolver resolver,
                            String subject, String eventCode, String dossierLabel,
                            String bodyMessage, Map<String, Object> variables) {
        if (!mailProperties.isEnabled()) {
            return;
        }
        if (mailProperties.getOverrideRecipient() != null && !mailProperties.getOverrideRecipient().isBlank()) {
            sendWorkflowNotification(null, subject, eventCode, dossierLabel, bodyMessage, variables);
            return;
        }
        List<String> emails = resolver.resolveEmails(userIds);
        if (emails.isEmpty()) {
            log.debug("Aucun e-mail utilisateur pour l'événement {}", eventCode);
            return;
        }
        for (String email : emails) {
            sendWorkflowNotification(email, subject, eventCode, dossierLabel, bodyMessage, variables);
        }
    }

    private String resolveTemplate(String eventCode) {
        if (eventCode == null || eventCode.isBlank()) {
            return "mail/workflow-notification";
        }
        if (eventCode.startsWith("CORRECTION_")) {
            return "mail/process/correction";
        }
        if (eventCode.startsWith("CERTIFICAT_")) {
            return "mail/process/certificat";
        }
        if (eventCode.startsWith("UTIL_DOUANE_")) {
            return "mail/process/utilisation-douane";
        }
        if (eventCode.startsWith("UTIL_TVA_")) {
            return "mail/process/utilisation-tva";
        }
        if (eventCode.startsWith("TRANSFERT_")) {
            return "mail/process/transfert";
        }
        if (eventCode.startsWith("CLOTURE_")) {
            return "mail/process/cloture";
        }
        return "mail/workflow-notification";
    }

    @Async
    public void sendPasswordResetRequestToAdmin(String toEmail, String username, String userEmail, String dateCreation) {
        if (!mailProperties.isEnabled()) {
            return;
        }
        String recipient = resolveRecipient(toEmail);
        if (recipient == null) {
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("username", username != null ? username : "");
            ctx.setVariable("email", userEmail != null ? userEmail : "");
            ctx.setVariable("dateCreation", dateCreation != null ? dateCreation : "");
            String html = templateEngine.process("mail/password-reset-request-admin", ctx);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.getFrom());
            helper.setTo(recipient);
            helper.setSubject("[SGCI] Nouvelle demande de réinitialisation de mot de passe — " + username);
            helper.setText(html, true);
            mailSender.send(mime);
            log.info("E-mail demande reset password envoyé à l'admin {}", recipient);
        } catch (MessagingException | RuntimeException e) {
            log.warn("Échec envoi e-mail demande reset password à l'admin {}: {}", recipient, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetApproved(String toEmail, String username, String newPassword) {
        if (!mailProperties.isEnabled()) {
            return;
        }
        String recipient = resolveRecipient(toEmail);
        if (recipient == null) {
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("username", username != null ? username : "");
            ctx.setVariable("newPassword", newPassword);
            String html = templateEngine.process("mail/password-reset-approved", ctx);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.getFrom());
            helper.setTo(recipient);
            helper.setSubject("[SGCI] Réinitialisation de votre mot de passe");
            helper.setText(html, true);
            mailSender.send(mime);
            log.info("E-mail reset password (approuvé) envoyé à {}", recipient);
        } catch (MessagingException | RuntimeException e) {
            log.warn("Échec envoi e-mail reset password approuvé à {}: {}", recipient, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetRejected(String toEmail, String username, String motif) {
        if (!mailProperties.isEnabled()) {
            return;
        }
        String recipient = resolveRecipient(toEmail);
        if (recipient == null) {
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("username", username != null ? username : "");
            ctx.setVariable("motif", motif != null ? motif : "");
            String html = templateEngine.process("mail/password-reset-rejected", ctx);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.getFrom());
            helper.setTo(recipient);
            helper.setSubject("[SGCI] Demande de réinitialisation refusée");
            helper.setText(html, true);
            mailSender.send(mime);
            log.info("E-mail reset password (refusé) envoyé à {}", recipient);
        } catch (MessagingException | RuntimeException e) {
            log.warn("Échec envoi e-mail reset password refusé à {}: {}", recipient, e.getMessage());
        }
    }

    private String resolveRecipient(String toEmail) {
        String override = mailProperties.getOverrideRecipient();
        if (override != null && !override.isBlank()) {
            return override;
        }
        if (toEmail == null || toEmail.isBlank()) {
            log.debug("E-mail reset password ignoré (adresse vide)");
            return null;
        }
        return toEmail;
    }
}
