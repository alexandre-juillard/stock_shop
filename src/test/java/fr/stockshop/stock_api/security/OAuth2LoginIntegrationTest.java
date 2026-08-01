package fr.stockshop.stock_api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.mail.EmailService;
import fr.stockshop.stock_api.user.dto.LoginResponse;
import fr.stockshop.stock_api.user.entity.OauthAccount;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.repository.OauthAccountRepository;
import fr.stockshop.stock_api.user.repository.UserRepository;
import fr.stockshop.stock_api.user.service.AuthenticationService;
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
 * Vérifie la redirection vers Google (GET /api/auth/oauth2/google) ainsi que la création/liaison de
 * compte lors d'une connexion Google.
 *
 * <p>Le callback complet (échange du code auprès de Google) n'est pas simulable sans serveur OAuth2
 * réel : {@link AuthenticationService#loginWithOAuth2} est donc appelé directement pour couvrir la
 * logique métier déclenchée par ce callback, tandis que le test MockMvc se limite à la redirection
 * initiale.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OAuth2LoginIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthenticationService authenticationService;
  @Autowired private UserRepository userRepository;
  @Autowired private OauthAccountRepository oauthAccountRepository;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void accessingGoogleAuthorizationEndpointRedirectsToGoogleConsentPage() throws Exception {
    mockMvc
        .perform(get("/api/auth/oauth2/google"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", containsString("accounts.google.com")));
  }

  @Test
  void firstLoginWithUnknownEmailCreatesActiveAccountWithoutPasswordAndLinksOauthAccount() {
    String providerUserId = UUID.randomUUID().toString();
    String email = "google-new-" + UUID.randomUUID() + "@test.fr";

    LoginResponse response =
        authenticationService.loginWithOAuth2(
            "google", providerUserId, email, "Alice", "Dupont", "https://img/avatar.png", "junit");

    assertThat(response.accessToken()).isNotBlank();
    assertThat(response.refreshToken()).isNotBlank();
    assertThat(response.user().email()).isEqualTo(email);

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.isActive()).isTrue();
    assertThat(user.getPasswordHash()).isNull();
    assertThat(user.getEmailConfirmedAt()).isNotNull();

    OauthAccount oauthAccount =
        oauthAccountRepository
            .findByProviderAndProviderUserId("google", providerUserId)
            .orElseThrow();
    assertThat(oauthAccount.getUser().getId()).isEqualTo(user.getId());
    assertThat(oauthAccount.getProviderEmail()).isEqualTo(email);
  }

  @Test
  void loginWithAlreadyLinkedGoogleAccountReusesExistingUserWithoutDuplication() {
    String providerUserId = UUID.randomUUID().toString();
    String email = "google-existing-" + UUID.randomUUID() + "@test.fr";

    LoginResponse firstLogin =
        authenticationService.loginWithOAuth2(
            "google", providerUserId, email, "Alice", "Dupont", null, "junit");
    LoginResponse secondLogin =
        authenticationService.loginWithOAuth2(
            "google", providerUserId, email, "Alice", "Dupont", null, "junit");

    assertThat(secondLogin.user().id()).isEqualTo(firstLogin.user().id());
    assertThat(secondLogin.refreshToken()).isNotEqualTo(firstLogin.refreshToken());

    long accountCount =
        oauthAccountRepository.findAll().stream()
            .filter(a -> providerUserId.equals(a.getProviderUserId()))
            .count();
    assertThat(accountCount).isEqualTo(1);
  }

  @Test
  void loginWithGoogleReusesExistingLocalAccountMatchingEmailWithoutTouchingPassword()
      throws Exception {
    String email = "google-link-existing-" + UUID.randomUUID() + "@test.fr";
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email", email,
                            "password", "Password123!",
                            "firstName", "Alice",
                            "lastName", "Dupont"))))
        .andExpect(status().isCreated());

    User existingUser = userRepository.findByEmail(email).orElseThrow();
    String passwordHashBeforeOAuth = existingUser.getPasswordHash();

    LoginResponse response =
        authenticationService.loginWithOAuth2(
            "google", UUID.randomUUID().toString(), email, "Alice", "Dupont", null, "junit");

    assertThat(response.user().id()).isEqualTo(existingUser.getId());
    User reloaded = userRepository.findByEmail(email).orElseThrow();
    assertThat(reloaded.getPasswordHash()).isEqualTo(passwordHashBeforeOAuth);
  }
}
