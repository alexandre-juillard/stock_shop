package fr.stockshop.stock_api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.mail.EmailService;
import fr.stockshop.stock_api.security.TokenService;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.entity.UserSession;
import fr.stockshop.stock_api.user.repository.UserRepository;
import fr.stockshop.stock_api.user.repository.UserSessionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vérifie le rafraîchissement de jetons (POST /api/auth/refresh) et la déconnexion (POST
 * /api/auth/logout).
 *
 * <p>{@link EmailService} est simulé ({@code @MockitoBean}) pour éviter toute tentative de
 * connexion SMTP réelle lors de l'inscription, hors périmètre de ce test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RefreshAndLogoutIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private UserSessionRepository userSessionRepository;
  @Autowired private TokenService tokenService;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void refreshingWithValidTokenRotatesTokensAndInvalidatesOldOne() throws Exception {
    String email = "refresh-ok-" + UUID.randomUUID() + "@test.fr";
    registerAndActivate(email);
    String oldRefreshToken = login(email).refreshToken();

    String body =
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshPayload(oldRefreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String newRefreshToken = objectMapper.readTree(body).get("refreshToken").asText();
    assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
    assertThat(userSessionRepository.findByTokenHash(tokenService.hashToken(newRefreshToken)))
        .isPresent();
    assertThat(userSessionRepository.findByTokenHash(tokenService.hashToken(oldRefreshToken)))
        .isEmpty();

    // L'ancien token, remplacé par la rotation, doit désormais être rejeté (détection de rejeu).
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshPayload(oldRefreshToken)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshingWithUnknownTokenReturnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshPayload(UUID.randomUUID().toString())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Refresh token invalide"));
  }

  @Test
  void refreshingWithExpiredTokenReturnsUnauthorizedAndDeletesSession() throws Exception {
    String email = "refresh-expired-" + UUID.randomUUID() + "@test.fr";
    registerAndActivate(email);
    String refreshToken = login(email).refreshToken();

    UserSession session =
        userSessionRepository.findByTokenHash(tokenService.hashToken(refreshToken)).orElseThrow();
    session.setExpiresAt(Instant.now().minusSeconds(60));
    userSessionRepository.save(session);

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshPayload(refreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Refresh token expiré ou révoqué"));

    assertThat(userSessionRepository.findByTokenHash(tokenService.hashToken(refreshToken)))
        .isEmpty();
  }

  @Test
  void refreshingWithBlankTokenReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshPayload("")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.refreshToken").exists());
  }

  @Test
  void loggingOutWithValidAccessTokenDeletesSessionAndReturnsNoContent() throws Exception {
    String email = "logout-ok-" + UUID.randomUUID() + "@test.fr";
    registerAndActivate(email);
    LoginResult login = login(email);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshPayload(login.refreshToken())))
        .andExpect(status().isNoContent());

    assertThat(userSessionRepository.findByTokenHash(tokenService.hashToken(login.refreshToken())))
        .isEmpty();
  }

  @Test
  void loggingOutOnlyInvalidatesTargetedSessionNotOtherDevices() throws Exception {
    String email = "logout-single-device-" + UUID.randomUUID() + "@test.fr";
    registerAndActivate(email);

    LoginResult deviceA = login(email);
    LoginResult deviceB = login(email);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer " + deviceA.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshPayload(deviceA.refreshToken())))
        .andExpect(status().isNoContent());

    assertThat(
            userSessionRepository.findByTokenHash(tokenService.hashToken(deviceA.refreshToken())))
        .isEmpty();
    assertThat(
            userSessionRepository.findByTokenHash(tokenService.hashToken(deviceB.refreshToken())))
        .isPresent();

    // La session de l'appareil B reste utilisable : seule celle de l'appareil A a été invalidée.
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshPayload(deviceB.refreshToken())))
        .andExpect(status().isOk());
  }

  @Test
  void loggingOutWithUnknownRefreshTokenStillReturnsNoContent() throws Exception {
    String email = "logout-unknown-" + UUID.randomUUID() + "@test.fr";
    registerAndActivate(email);
    LoginResult login = login(email);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshPayload(UUID.randomUUID().toString())))
        .andExpect(status().isNoContent());
  }

  private record LoginResult(String accessToken, String refreshToken) {}

  private LoginResult login(String email) throws Exception {
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

    var json = objectMapper.readTree(body);
    return new LoginResult(json.get("accessToken").asText(), json.get("refreshToken").asText());
  }

  private User registerAndActivate(String email) throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email)))
        .andExpect(status().isCreated());

    User user = userRepository.findByEmail(email).orElseThrow();
    user.setActive(true);
    return userRepository.save(user);
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

  private String refreshPayload(String refreshToken) throws Exception {
    return objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));
  }
}

