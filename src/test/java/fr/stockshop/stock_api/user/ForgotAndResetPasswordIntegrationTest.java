package fr.stockshop.stock_api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.mail.EmailService;
import fr.stockshop.stock_api.security.TokenService;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.repository.UserRepository;
import fr.stockshop.stock_api.user.repository.UserSessionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vérifie la réinitialisation de mot de passe (POST /api/auth/forgot-password et POST
 * /api/auth/reset-password).
 *
 * <p>{@link EmailService} est simulé ({@code @MockitoBean}) afin de vérifier uniquement l'état en
 * base et l'interaction, sans dépendre d'un serveur SMTP réel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ForgotAndResetPasswordIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private UserSessionRepository userSessionRepository;
  @Autowired private TokenService tokenService;
  @Autowired private PasswordEncoder passwordEncoder;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void forgotPasswordWithExistingEmailReturns200AndSendsEmail() throws Exception {
    String email = registerAndActivate("forgot-ok-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotPasswordPayload(email)))
        .andExpect(status().isOk());

    ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendPasswordResetEmail(any(), tokenCaptor.capture());

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.getResetTokenHash()).isNotBlank();
    assertThat(tokenService.hashToken(tokenCaptor.getValue())).isEqualTo(user.getResetTokenHash());
    assertThat(user.getResetTokenExpiresAt()).isAfter(Instant.now());
  }

  @Test
  void forgotPasswordWithUnknownEmailReturns200AndSendsNoEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotPasswordPayload("unknown-" + UUID.randomUUID() + "@test.fr")))
        .andExpect(status().isOk());

    verify(emailService, never()).sendPasswordResetEmail(any(), any());
  }

  @Test
  void requestingASecondResetTokenInvalidatesThePreviousOne() throws Exception {
    String email = registerAndActivate("forgot-twice-" + UUID.randomUUID() + "@test.fr");
    String firstToken = requestResetToken(email);

    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotPasswordPayload(email)))
        .andExpect(status().isOk());

    // Un seul token de reset actif par utilisateur : le premier n'est plus valide.
    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPasswordPayload(firstToken, "NouveauMotDePasse1!")))
        .andExpect(status().isNotFound());
  }

  @Test
  void resettingWithValidTokenUpdatesPasswordInvalidatesTokenAndRevokesSessions() throws Exception {
    String email = registerAndActivate("reset-ok-" + UUID.randomUUID() + "@test.fr");

    // Une session active existe (issue d'un login) : elle doit être supprimée après le reset.
    String refreshToken = login(email);
    assertThat(userSessionRepository.findByTokenHash(tokenService.hashToken(refreshToken)))
        .isPresent();

    String rawToken = requestResetToken(email);

    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPasswordPayload(rawToken, "NouveauMotDePasse1!")))
        .andExpect(status().isOk());

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(passwordEncoder.matches("NouveauMotDePasse1!", user.getPasswordHash())).isTrue();
    assertThat(user.getResetTokenHash()).isNull();
    assertThat(user.getResetTokenExpiresAt()).isNull();
    assertThat(userSessionRepository.findByTokenHash(tokenService.hashToken(refreshToken)))
        .isEmpty();

    // Le token utilisé ne doit plus être réutilisable.
    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPasswordPayload(rawToken, "EncoreUnAutre1!")))
        .andExpect(status().isNotFound());
  }

  @Test
  void resettingWithUnknownTokenReturnsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPasswordPayload("token-inexistant", "NouveauMotDePasse1!")))
        .andExpect(status().isNotFound());
  }

  @Test
  void resettingWithExpiredTokenReturnsGone() throws Exception {
    String email = registerAndActivate("reset-expired-" + UUID.randomUUID() + "@test.fr");
    String rawToken = requestResetToken(email);

    User user = userRepository.findByEmail(email).orElseThrow();
    user.setResetTokenExpiresAt(Instant.now().minusSeconds(60));
    userRepository.save(user);

    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPasswordPayload(rawToken, "NouveauMotDePasse1!")))
        .andExpect(status().isGone());

    User reloaded = userRepository.findByEmail(email).orElseThrow();
    assertThat(reloaded.getResetTokenHash()).isNull();
  }

  @Test
  void resettingWithShortPasswordReturnsBadRequest() throws Exception {
    String email = registerAndActivate("reset-short-pwd-" + UUID.randomUUID() + "@test.fr");
    String rawToken = requestResetToken(email);

    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPasswordPayload(rawToken, "short")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
  }

  private String requestResetToken(String email) throws Exception {
    ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotPasswordPayload(email)))
        .andExpect(status().isOk());
    verify(emailService).sendPasswordResetEmail(any(), tokenCaptor.capture());
    return tokenCaptor.getValue();
  }

  private String login(String email) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readTree(body).get("refreshToken").asText();
  }

  private String registerAndActivate(String email) throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email)))
        .andExpect(status().isCreated());

    User user = userRepository.findByEmail(email).orElseThrow();
    user.setActive(true);
    userRepository.save(user);
    return email;
  }

  private String registerPayload(String email) throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "email", email,
            "password", "Password123!",
            "firstName", "Alice",
            "lastName", "Dupont"));
  }

  private String loginPayload(String email) throws Exception {
    return objectMapper.writeValueAsString(
        Map.of("email", email, "password", "Password123!", "rememberMe", false));
  }

  private String forgotPasswordPayload(String email) throws Exception {
    return objectMapper.writeValueAsString(Map.of("email", email));
  }

  private String resetPasswordPayload(String token, String newPassword) throws Exception {
    return objectMapper.writeValueAsString(Map.of("token", token, "newPassword", newPassword));
  }
}
