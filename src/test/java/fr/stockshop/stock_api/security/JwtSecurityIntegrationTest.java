package fr.stockshop.stock_api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vérifie que la configuration Spring Security + JWT respecte les critères d'acceptation du ticket
 * INF-003 :
 *
 * <ul>
 *   <li>AC-1 : /api/auth (sauf /logout) accessible sans token
 *   <li>AC-2 : toute autre ressource retourne 401 sans token valide
 *   <li>AC-4 : le JWT contient userId, email (sub), iat, exp
 *   <li>AC-5 : un token expiré retourne 401
 *   <li>AC-6 : un token à la signature altérée retourne 401
 *   <li>AC-7 : le filtre authentifie correctement une requête avec un token valide
 * </ul>
 *
 * <p>Les comptes étant créés inactifs tant qu'ils ne sont pas confirmés par email, les tests
 * ci-dessous activent directement le compte via le repository entre l'inscription et la connexion :
 * ce test cible le filtre JWT, pas le flux de confirmation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class JwtSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${security.jwt.secret}")
  private String jwtSecret;

  @Test
  void registerLoginAndRefreshAreAccessibleWithoutToken() throws Exception {
    String email = "public-" + UUID.randomUUID() + "@test.fr";

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email)))
        .andExpect(status().isCreated());
    activateUser(email);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(email)))
        .andExpect(status().isOk());
  }

  @Test
  void healthEndpointsAreAccessibleWithoutToken() throws Exception {
    mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void logoutIsRejectedWithoutToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "whatever"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpointRejectsMissingToken() throws Exception {
    // Aucun endpoint métier n'est encore implémenté : on vérifie sur une ressource
    // arbitraire non publique, qui doit être bloquée par la règle anyRequest().authenticated().
    mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutSucceedsWithValidAccessToken() throws Exception {
    String email = "valid-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerAndLogin(email);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("refreshToken", "does-not-matter"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void expiredTokenIsRejected() throws Exception {
    String email = "expired-" + UUID.randomUUID() + "@test.fr";
    registerAndLogin(email);

    String expiredToken =
        buildRawToken(email, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60));

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "x"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void tamperedTokenSignatureIsRejected() throws Exception {
    String email = "tampered-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerAndLogin(email);

    // On altère un caractère au milieu de la signature (dernier segment après le dernier '.')
    // plutôt que le dernier caractère, qui peut correspondre à des bits de bourrage Base64URL
    // ignorés au décodage et donc ne pas produire de signature réellement différente.
    int lastDot = accessToken.lastIndexOf('.');
    int signatureMiddle = lastDot + 1 + (accessToken.length() - lastDot - 1) / 2;

    char original = accessToken.charAt(signatureMiddle);
    char replacement = original == 'A' ? 'B' : 'A';

    String tampered =
        accessToken.substring(0, signatureMiddle)
            + replacement
            + accessToken.substring(signatureMiddle + 1);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer " + tampered)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "x"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void accessTokenContainsRequiredClaims() throws Exception {
    String email = "claims-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerAndLogin(email);

    var claims =
        Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(accessToken)
            .getPayload();

    assertThat(claims.getSubject()).isEqualTo(email);
    assertThat(claims.get("email", String.class)).isEqualTo(email);
    assertThat(claims.get("userId", String.class)).isNotBlank();
    assertThat(UUID.fromString(claims.get("userId", String.class))).isNotNull();
    assertThat(claims.getIssuedAt()).isNotNull();
    assertThat(claims.getExpiration()).isAfter(new Date());
  }

  private String registerAndLogin(String email) throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email)))
        .andExpect(status().isCreated());
    activateUser(email);

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

    return objectMapper.readTree(body).get("accessToken").asText();
  }

  private void activateUser(String email) {
    userRepository
        .findByEmail(email)
        .ifPresent(
            user -> {
              user.setActive(true);
              userRepository.save(user);
            });
  }

  private String registerPayload(String email) throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "email", email,
            "password", "Password123!",
            "firstName", "Test",
            "lastName", "User"));
  }

  private String loginPayload(String email) throws Exception {
    return objectMapper.writeValueAsString(Map.of("email", email, "password", "Password123!"));
  }

  private String buildRawToken(String email, Instant issuedAt, Instant expiration) {
    return Jwts.builder()
        .claims(Map.of("userId", UUID.randomUUID().toString(), "email", email, "type", "access"))
        .subject(email)
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(expiration))
        .signWith(getSigningKey())
        .compact();
  }

  private SecretKey getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
