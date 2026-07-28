package fr.stockshop.stock_api.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.user.entity.Role;
import fr.stockshop.stock_api.user.entity.User;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Vérifie les critères d'acceptation du ticket "service d'email" :
 *
 * <p>Utilise GreenMail, un serveur SMTP en mémoire, sur le port 3025 (configuré par défaut dans
 * {@code application-test.yml}). L'envoi étant asynchrone ({@code @Async}), {@code
 * waitForIncomingEmail} attend activement la réception plutôt que de fixer un délai arbitraire.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class EmailServiceTest {

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

  @Autowired private EmailService emailService;

  @Test
  void sendAccountConfirmationEmailShouldDeliverHtmlEmailWithTokenLink() throws Exception {
    User user =
        User.builder()
            .email("confirmation-" + UUID.randomUUID() + "@test.fr")
            .firstName("Alice")
            .lastName("Dupont")
            .role(Role.USER)
            .build();
    String rawToken = "raw-confirmation-token";

    emailService.sendAccountConfirmationEmail(user, rawToken);

    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage received = greenMail.getReceivedMessages()[0];

    assertThat(received.getSubject()).isEqualTo("Confirmez votre compte Stock & Shop");
    assertThat(received.getAllRecipients()[0].toString()).isEqualTo(user.getEmail());
    assertThat(received.getContentType()).contains("text/html");

    String body = (String) received.getContent();
    assertThat(body).contains("Alice");
    assertThat(body).contains("token=" + rawToken);
  }

  @Test
  void sendPasswordResetEmailShouldDeliverHtmlEmailWithTokenLink() throws Exception {
    User user =
        User.builder()
            .email("reset-" + UUID.randomUUID() + "@test.fr")
            .firstName("Bob")
            .lastName("Martin")
            .role(Role.USER)
            .build();
    String rawToken = "raw-reset-token";

    emailService.sendPasswordResetEmail(user, rawToken);

    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage received = greenMail.getReceivedMessages()[0];

    assertThat(received.getSubject())
        .isEqualTo("Réinitialisation de votre mot de passe Stock & Shop");
    assertThat(received.getAllRecipients()[0].toString()).isEqualTo(user.getEmail());

    String body = (String) received.getContent();
    assertThat(body).contains("Bob");
    assertThat(body).contains("token=" + rawToken);
  }
}
