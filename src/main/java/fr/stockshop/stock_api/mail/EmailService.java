package fr.stockshop.stock_api.mail;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.stockshop.stock_api.user.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Envoie les emails transactionnels de l'application (confirmation de compte, réinitialisation de
 * mot de passe) au format HTML, via SMTP.
 */
@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;

  @Value("${app.mail.from}")
  private String mailFrom;

  @Value("${app.frontend.base-url}")
  private String frontendBaseUrl;

  @Value("${app.mail.confirmation-path:/confirm-account}")
  private String confirmationPath;

  @Value("${app.mail.reset-password-path:/reset-password}")
  private String resetPasswordPath;

  @Value("${app.mail.token-expiration:24h}")
  private Duration tokenExpiration;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "mailSender et templateEngine sont des beans Spring singletons partagés et immuables"
              + " du point de vue de ce service ; aucune copie défensive n'est pertinente ici.")
  public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
  }

  /** Envoie l'email de confirmation de compte contenant le lien expirable. */
  @Async
  public void sendAccountConfirmationEmail(User user, String rawToken) {
    Context context = new Context();
    context.setVariable("firstName", user.getFirstName());
    context.setVariable(
        "confirmationLink", frontendBaseUrl + confirmationPath + "?token=" + rawToken);
    context.setVariable("expirationHours", tokenExpiration.toHours());

    send(
        user.getEmail(),
        "Confirmez votre compte Stock & Shop",
        "email/account-confirmation",
        context);
  }

  /** Envoie l'email de réinitialisation de mot de passe contenant le lien expirable. */
  @Async
  public void sendPasswordResetEmail(User user, String rawToken) {
    Context context = new Context();
    context.setVariable("firstName", user.getFirstName());
    context.setVariable("resetLink", frontendBaseUrl + resetPasswordPath + "?token=" + rawToken);
    context.setVariable("expirationHours", tokenExpiration.toHours());

    send(
        user.getEmail(),
        "Réinitialisation de votre mot de passe Stock & Shop",
        "email/reset-password",
        context);
  }

  private void send(String to, String subject, String templateName, Context context) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setFrom(mailFrom);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(templateEngine.process(templateName, context), true);

      mailSender.send(message);
    } catch (MessagingException | MailException ex) {
      // AC-3 : un échec d'envoi ne doit jamais faire échouer la requête HTTP appelante.
      log.error("Échec de l'envoi de l'email '{}' à {}", templateName, to, ex);
    }
  }
}
