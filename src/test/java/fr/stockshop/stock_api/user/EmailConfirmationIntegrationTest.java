package fr.stockshop.stock_api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.security.TokenService;
import fr.stockshop.stock_api.user.entity.Role;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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

/**
 * Vérifie l'activation de compte (POST /api/auth/confirm-email) et le renvoi de confirmation (POST
 * /api/auth/resend-confirmation).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class EmailConfirmationIntegrationTest {

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private TokenService tokenService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void confirmingWithValidTokenActivatesAccountAndClearsToken() throws Exception {
    String rawToken = tokenService.generateToken();
    User user =
        createInactiveUser(
            "confirm-ok-" + UUID.randomUUID() + "@test.fr",
            rawToken,
            Instant.now().plusSeconds(3600));

    mockMvc
        .perform(
            post("/api/auth/confirm-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", rawToken))))
        .andExpect(status().isOk());

    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.isActive()).isTrue();
    assertThat(reloaded.getEmailConfirmedAt()).isNotNull();
    assertThat(reloaded.getConfirmationTokenHash()).isNull();
    assertThat(reloaded.getConfirmationTokenExpiresAt()).isNull();
  }

  @Test
  void confirmingWithUnknownTokenReturnsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/confirm-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "token-inexistant"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void confirmingWithExpiredTokenReturnsGone() throws Exception {
    String rawToken = tokenService.generateToken();
    createInactiveUser(
        "confirm-expired-" + UUID.randomUUID() + "@test.fr",
        rawToken,
        Instant.now().minusSeconds(60));

    mockMvc
        .perform(
            post("/api/auth/confirm-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", rawToken))))
        .andExpect(status().isGone());
  }

  @Test
  void resendingConfirmationForInactiveAccountSendsNewEmailAndInvalidatesOldToken()
      throws Exception {
    String rawToken = tokenService.generateToken();
    String email = "resend-ok-" + UUID.randomUUID() + "@test.fr";
    User user = createInactiveUser(email, rawToken, Instant.now().plusSeconds(3600));
    String oldHash = user.getConfirmationTokenHash();

    mockMvc
        .perform(
            post("/api/auth/resend-confirmation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))))
        .andExpect(status().isOk());

    User reloaded = userRepository.findByEmail(email).orElseThrow();
    assertThat(reloaded.getConfirmationTokenHash()).isNotEqualTo(oldHash);
    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

    // L'ancien token n'est plus valide : le nouveau a pris sa place en base.
    mockMvc
        .perform(
            post("/api/auth/confirm-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", rawToken))))
        .andExpect(status().isNotFound());
  }

  @Test
  void resendingConfirmationForActiveAccountReturnsConflict() throws Exception {
    String email = "resend-active-" + UUID.randomUUID() + "@test.fr";
    createActiveUser(email);

    mockMvc
        .perform(
            post("/api/auth/resend-confirmation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))))
        .andExpect(status().isConflict());
  }

  @Test
  void resendingConfirmationForUnknownEmailReturnsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/resend-confirmation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", "inconnu-" + UUID.randomUUID() + "@test.fr"))))
        .andExpect(status().isNotFound());
  }

  private User createInactiveUser(String email, String rawToken, Instant expiresAt) {
    User user =
        User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode("Password123!"))
            .firstName("Alice")
            .lastName("Dupont")
            .role(Role.USER)
            .active(false)
            .confirmationTokenHash(tokenService.hashToken(rawToken))
            .confirmationTokenExpiresAt(expiresAt)
            .build();
    return userRepository.save(user);
  }

  private User createActiveUser(String email) {
    User user =
        User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode("Password123!"))
            .firstName("Alice")
            .lastName("Dupont")
            .role(Role.USER)
            .active(true)
            .build();
    return userRepository.save(user);
  }
}
