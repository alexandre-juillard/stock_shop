package fr.stockshop.stock_api.mail;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.user.entity.Role;
import fr.stockshop.stock_api.user.entity.User;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.TemplateEngine;

/**
 * Vérifie un échec d'envoi (SMTP injoignable, erreur de rendu...) est absorbé par le service et ne
 * doit jamais remonter vers l'appelant.
 *
 * <p>Instancié manuellement (hors contexte Spring) : l'annotation {@code @Async} est donc inerte
 * ici et les méthodes s'exécutent de façon synchrone dans le thread de test, ce qui permet
 * d'affirmer directement l'absence de propagation d'exception, sans dépendre d'un délai d'attente.
 */
class EmailServiceFailureTest {

  private EmailService emailService;
  private TemplateEngine templateEngine;

  @BeforeEach
  void setUp() throws Exception {
    // JavaMailSenderImpl pointe vers un hôte volontairement injoignable : send() échouera.
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost("host-injoignable.invalid");
    mailSender.setPort(2525);

    templateEngine = mock(TemplateEngine.class);
    when(templateEngine.process(anyString(), any())).thenReturn("<html>contenu</html>");

    emailService = new EmailService(mailSender, templateEngine);
    setField(emailService, "mailFrom", "no-reply@stockshop.fr");
    setField(emailService, "frontendBaseUrl", "http://localhost:3000");
    setField(emailService, "confirmationPath", "/confirm-account");
    setField(emailService, "resetPasswordPath", "/reset-password");
    setField(emailService, "tokenExpiration", Duration.ofHours(24));
  }

  @Test
  void sendAccountConfirmationEmailShouldNotThrowWhenSmtpIsUnreachable() {
    User user = User.builder().email("user@test.fr").firstName("Alice").role(Role.USER).build();

    assertThatCode(() -> emailService.sendAccountConfirmationEmail(user, "raw-token"))
        .doesNotThrowAnyException();
  }

  @Test
  void sendPasswordResetEmailShouldNotThrowWhenSmtpIsUnreachable() {
    User user = User.builder().email("user@test.fr").firstName("Bob").role(Role.USER).build();

    assertThatCode(() -> emailService.sendPasswordResetEmail(user, "raw-token"))
        .doesNotThrowAnyException();
  }

  @Test
  void sendShouldNotThrowWhenTemplateRenderingFails() {
    when(templateEngine.process(anyString(), any()))
        .thenThrow(new MailSendException("erreur de rendu simulee"));
    User user = User.builder().email("user@test.fr").firstName("Alice").role(Role.USER).build();

    assertThatCode(() -> emailService.sendAccountConfirmationEmail(user, "raw-token"))
        .doesNotThrowAnyException();
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = EmailService.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
