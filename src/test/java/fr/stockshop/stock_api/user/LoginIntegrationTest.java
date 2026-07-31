package fr.stockshop.stock_api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vérifie la connexion email/mot de passe (POST /api/auth/login).
 *
 * <p>L'inscription préalable (via {@code registerAndActivate}) déclenche normalement l'envoi d'un
 * email de confirmation : {@link EmailService} est simulé ({@code @MockitoBean}) pour éviter toute
 * tentative de connexion SMTP réelle, hors périmètre de ce test (déjà couvert par {@code
 * EmailServiceTest} et {@code RegistrationIntegrationTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LoginIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private UserSessionRepository userSessionRepository;
  @Autowired private TokenService tokenService;
  @MockitoBean private EmailService emailService;

  @Value("${security.jwt.secret}")
  private String jwtSecret;

  @Value("${security.jwt.refresh-token-expiration}")
  private long rememberMeExpiration;

  @Value("${security.session.default-expiration}")
  private long defaultSessionExpiration;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void loginWithValidCredentialsReturns200WithTokensAndUser() throws Exception {
    String email = registerAndActivate("valid-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(email, "Password123!", false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.user.email").value(email))
        .andExpect(jsonPath("$.user.firstName").value("Alice"))
        .andExpect(jsonPath("$.user.lastName").value("Dupont"))
        .andExpect(jsonPath("$.user.theme").value("light"))
        .andExpect(jsonPath("$.user.expirationAlertDays").value(3));
  }

  @Test
  void loginWithRememberMeTrueCreatesSessionWithSevenDayExpiration() throws Exception {
    String email = registerAndActivate("remember-" + UUID.randomUUID() + "@test.fr");

    String body =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload(email, "Password123!", true)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String rawRefreshToken = objectMapper.readTree(body).get("refreshToken").asText();
    var session =
        userSessionRepository
            .findByTokenHash(tokenService.hashToken(rawRefreshToken))
            .orElseThrow();

    assertThat(session.getExpiresAt())
        .isCloseTo(Instant.now().plusMillis(rememberMeExpiration), within(Duration.ofMinutes(1)));
  }

  @Test
  void loginWithoutRememberMeCreatesSessionWithDefaultExpiration() throws Exception {
    String email = registerAndActivate("no-remember-" + UUID.randomUUID() + "@test.fr");

    String body =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload(email, "Password123!", false)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String rawRefreshToken = objectMapper.readTree(body).get("refreshToken").asText();
    var session =
        userSessionRepository
            .findByTokenHash(tokenService.hashToken(rawRefreshToken))
            .orElseThrow();

    assertThat(session.getExpiresAt())
        .isCloseTo(
            Instant.now().plusMillis(defaultSessionExpiration), within(Duration.ofMinutes(1)));
    assertThat(session.getExpiresAt())
        .isBefore(Instant.now().plusMillis(rememberMeExpiration).minus(Duration.ofDays(1)));
  }

  @Test
  void loginWithWrongPasswordReturnsGenericUnauthorized() throws Exception {
    String email = registerAndActivate("wrong-pwd-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(email, "MauvaisMotDePasse1!", false)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
  }

  @Test
  void loginWithInactiveAccountReturnsForbidden() throws Exception {
    String email = "inactive-" + UUID.randomUUID() + "@test.fr";
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email)))
        .andExpect(status().isCreated());
    // Compte volontairement laissé inactif (pas d'appel à confirm-email).

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(email, "Password123!", false)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Le compte n'a pas encore été confirmé"));
  }

  @Test
  void accessTokenExpiresInConfiguredDuration() throws Exception {
    String email = registerAndActivate("expiry-" + UUID.randomUUID() + "@test.fr");

    String body =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload(email, "Password123!", false)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String accessToken = objectMapper.readTree(body).get("accessToken").asText();
    var claims =
        Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(accessToken)
            .getPayload();

    long durationMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
    assertThat(Duration.ofMillis(durationMillis))
        .isCloseTo(Duration.ofMinutes(15), Duration.ofSeconds(5));
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

  private String loginPayload(String email, String password, boolean rememberMe) throws Exception {
    return objectMapper.writeValueAsString(
        Map.of("email", email, "password", password, "rememberMe", rememberMe));
  }

  private SecretKey getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
