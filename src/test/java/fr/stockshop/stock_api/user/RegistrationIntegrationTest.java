package fr.stockshop.stock_api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.security.TokenService;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Vérifie le flux d'inscription publique (POST /api/auth/register). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RegistrationIntegrationTest {

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private TokenService tokenService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void registeringWithValidDataCreatesInactiveAccountAndSendsConfirmationEmail() throws Exception {
    String email = "register-" + UUID.randomUUID() + "@test.fr";

    String responseBody =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerPayload(email, "Password123!", "Alice", "Dupont")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(responseBody).doesNotContain("Password123!");

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.isActive()).isFalse();
    assertThat(user.getPasswordHash()).isNotEqualTo("Password123!").startsWith("$2");
    assertThat(passwordEncoder.matches("Password123!", user.getPasswordHash())).isTrue();
    assertThat(user.getConfirmationTokenHash()).isNotBlank();
    assertThat(user.getConfirmationTokenExpiresAt())
        .isAfter(Instant.now())
        .isBefore(Instant.now().plus(25, ChronoUnit.HOURS));

    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage received = greenMail.getReceivedMessages()[0];
    assertThat(received.getAllRecipients()[0].toString()).isEqualTo(email);

    String rawTokenFromEmail = extractToken((String) received.getContent());
    assertThat(tokenService.hashToken(rawTokenFromEmail))
        .isEqualTo(user.getConfirmationTokenHash());
    assertThat(rawTokenFromEmail).isNotEqualTo(user.getConfirmationTokenHash());
  }

  @Test
  void registeringWithExistingEmailReturnsConflictWithoutCreatingDuplicate() throws Exception {
    String email = "duplicate-" + UUID.randomUUID() + "@test.fr";
    String payload = registerPayload(email, "Password123!", "Alice", "Dupont");

    mockMvc
        .perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isConflict());

    long matchingAccounts =
        userRepository.findAll().stream().filter(u -> email.equals(u.getEmail())).count();
    assertThat(matchingAccounts).isEqualTo(1);
  }

  @Test
  void registeringWithShortPasswordReturnsBadRequestOnPasswordField() throws Exception {
    String email = "short-pwd-" + UUID.randomUUID() + "@test.fr";

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email, "short", "Alice", "Dupont")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.password").exists());

    assertThat(userRepository.findByEmail(email)).isEmpty();
  }

  @Test
  void registeringWithMissingFieldsReturnsBadRequestListingAllInvalidFields() throws Exception {
    String payload = objectMapper.writeValueAsString(Map.of("email", ""));

    mockMvc
        .perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").exists())
        .andExpect(jsonPath("$.fieldErrors.password").exists())
        .andExpect(jsonPath("$.fieldErrors.firstName").exists())
        .andExpect(jsonPath("$.fieldErrors.lastName").exists());
  }

  private String registerPayload(String email, String password, String firstName, String lastName)
      throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "email", email,
            "password", password,
            "firstName", firstName,
            "lastName", lastName));
  }

  private String extractToken(String emailBody) {
    Matcher matcher = Pattern.compile("token=([\\w-]+)").matcher(emailBody);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }
}
