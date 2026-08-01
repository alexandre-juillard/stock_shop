package fr.stockshop.stock_api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.mail.EmailService;
import fr.stockshop.stock_api.user.dto.LinkDecision;
import fr.stockshop.stock_api.user.dto.LinkDecisionRequest;
import fr.stockshop.stock_api.user.dto.LoginResponse;
import fr.stockshop.stock_api.user.dto.OAuth2LoginOutcome;
import fr.stockshop.stock_api.user.entity.OauthAccount;
import fr.stockshop.stock_api.user.entity.OauthLinkContext;
import fr.stockshop.stock_api.user.entity.Role;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.repository.OauthAccountRepository;
import fr.stockshop.stock_api.user.repository.OauthLinkContextRepository;
import fr.stockshop.stock_api.user.repository.OauthLinkDecisionRepository;
import fr.stockshop.stock_api.user.repository.UserRepository;
import fr.stockshop.stock_api.user.service.AuthenticationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
 * Vérifie la redirection vers Google (GET /api/auth/oauth2/google) ainsi que la création/liaison de
 * compte lors d'une connexion Google, y compris la liaison de comptes OAuth2 ↔ local (AUTH-007).
 *
 * <p>Le callback complet (échange du code auprès de Google) n'est pas simulable sans serveur OAuth2
 * réel : {@link AuthenticationService#loginWithOAuth2} est donc appelé directement pour couvrir la
 * logique métier déclenchée par ce callback, tandis que le test MockMvc se limite à la redirection
 * initiale et à l'endpoint POST /api/auth/oauth2/link-decision.
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
  @Autowired private OauthLinkContextRepository oauthLinkContextRepository;
  @Autowired private OauthLinkDecisionRepository oauthLinkDecisionRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private TokenService tokenService;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private User saveLocalUser(String email, boolean active) {
    return userRepository.save(
        User.builder()
            .email(email)
            .firstName("Alice")
            .lastName("Dupont")
            .passwordHash(passwordEncoder.encode("Password123!"))
            .role(Role.USER)
            .active(active)
            .emailConfirmedAt(active ? Instant.now() : null)
            .build());
  }

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

    OAuth2LoginOutcome outcome =
        authenticationService.loginWithOAuth2(
            "google", providerUserId, email, "Alice", "Dupont", "https://img/avatar.png", "junit");

    assertThat(outcome.requiresLink()).isFalse();
    LoginResponse response = outcome.tokens();
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
        authenticationService
            .loginWithOAuth2("google", providerUserId, email, "Alice", "Dupont", null, "junit")
            .tokens();
    LoginResponse secondLogin =
        authenticationService
            .loginWithOAuth2("google", providerUserId, email, "Alice", "Dupont", null, "junit")
            .tokens();

    assertThat(secondLogin.user().id()).isEqualTo(firstLogin.user().id());
    assertThat(secondLogin.refreshToken()).isNotEqualTo(firstLogin.refreshToken());

    long accountCount =
        oauthAccountRepository.findAll().stream()
            .filter(a -> providerUserId.equals(a.getProviderUserId()))
            .count();
    assertThat(accountCount).isEqualTo(1);
  }

  @Test
  void loginWithGoogleMatchingActiveLocalAccountRequiresLinkConfirmationWithoutIssuingTokens() {
    String email = "google-link-active-" + UUID.randomUUID() + "@test.fr";
    User existingUser = saveLocalUser(email, true);
    String passwordHashBeforeOAuth = existingUser.getPasswordHash();

    OAuth2LoginOutcome outcome =
        authenticationService.loginWithOAuth2(
            "google", UUID.randomUUID().toString(), email, "Alice", "Dupont", null, "junit");

    assertThat(outcome.requiresLink()).isTrue();
    assertThat(outcome.linkRequired().status()).isEqualTo("LINK_REQUIRED");
    assertThat(outcome.linkRequired().linkContext()).isNotBlank();

    User reloaded = userRepository.findByEmail(email).orElseThrow();
    assertThat(reloaded.getPasswordHash()).isEqualTo(passwordHashBeforeOAuth);
    assertThat(
            oauthAccountRepository.findAll().stream()
                .anyMatch(a -> a.getUser().getId().equals(existingUser.getId())))
        .isFalse();
  }

  @Test
  void resolvingLinkDecisionWithLinkAttachesGoogleAccountToExistingUserAndIssuesTokens() {
    String email = "google-link-decision-" + UUID.randomUUID() + "@test.fr";
    String providerUserId = UUID.randomUUID().toString();
    User existingUser = saveLocalUser(email, true);

    String linkContext =
        authenticationService
            .loginWithOAuth2("google", providerUserId, email, "Alice", "Dupont", null, "junit")
            .linkRequired()
            .linkContext();

    LoginResponse response =
        authenticationService.resolveOAuth2LinkDecision(
            new LinkDecisionRequest(linkContext, LinkDecision.LINK), "junit");

    assertThat(response.user().id()).isEqualTo(existingUser.getId());

    OauthAccount oauthAccount =
        oauthAccountRepository
            .findByProviderAndProviderUserId("google", providerUserId)
            .orElseThrow();
    assertThat(oauthAccount.getUser().getId()).isEqualTo(existingUser.getId());

    assertThat(
            oauthLinkDecisionRepository
                .findByUser_IdAndProviderAndProviderUserId(
                    existingUser.getId(), "google", providerUserId)
                .orElseThrow()
                .isLinked())
        .isTrue();

    assertThat(oauthLinkContextRepository.findByTokenHash(tokenService.hashToken(linkContext)))
        .isEmpty();
  }

  @Test
  void resolvingLinkDecisionWithDeclineCreatesIndependentAccountWithoutTouchingOriginalPassword() {
    String email = "google-decline-decision-" + UUID.randomUUID() + "@test.fr";
    String providerUserId = UUID.randomUUID().toString();
    User existingUser = saveLocalUser(email, true);
    String passwordHashBeforeOAuth = existingUser.getPasswordHash();

    String linkContext =
        authenticationService
            .loginWithOAuth2("google", providerUserId, email, "Alice", "Dupont", null, "junit")
            .linkRequired()
            .linkContext();

    LoginResponse response =
        authenticationService.resolveOAuth2LinkDecision(
            new LinkDecisionRequest(linkContext, LinkDecision.DECLINE), "junit");

    assertThat(response.user().id()).isNotEqualTo(existingUser.getId());

    User reloadedOriginal = userRepository.findById(existingUser.getId()).orElseThrow();
    assertThat(reloadedOriginal.getPasswordHash()).isEqualTo(passwordHashBeforeOAuth);

    OauthAccount oauthAccount =
        oauthAccountRepository
            .findByProviderAndProviderUserId("google", providerUserId)
            .orElseThrow();
    assertThat(oauthAccount.getUser().getId()).isEqualTo(response.user().id());

    assertThat(
            oauthLinkDecisionRepository
                .findByUser_IdAndProviderAndProviderUserId(
                    existingUser.getId(), "google", providerUserId)
                .orElseThrow()
                .isLinked())
        .isFalse();
  }

  @Test
  void repeatedLoginAfterLinkDecisionDoesNotAskAgain() {
    String email = "google-repeat-decision-" + UUID.randomUUID() + "@test.fr";
    String providerUserId = UUID.randomUUID().toString();
    saveLocalUser(email, true);

    String linkContext =
        authenticationService
            .loginWithOAuth2("google", providerUserId, email, "Alice", "Dupont", null, "junit")
            .linkRequired()
            .linkContext();
    LoginResponse firstResolved =
        authenticationService.resolveOAuth2LinkDecision(
            new LinkDecisionRequest(linkContext, LinkDecision.LINK), "junit");

    OAuth2LoginOutcome secondOutcome =
        authenticationService.loginWithOAuth2(
            "google", providerUserId, email, "Alice", "Dupont", null, "junit");

    assertThat(secondOutcome.requiresLink()).isFalse();
    assertThat(secondOutcome.tokens().user().id()).isEqualTo(firstResolved.user().id());
  }

  @Test
  void loginWithGoogleMatchingInactiveLocalAccountLinksAutomaticallyAndActivatesAccount() {
    String email = "google-inactive-" + UUID.randomUUID() + "@test.fr";
    User existingUser = saveLocalUser(email, false);
    assertThat(existingUser.isActive()).isFalse();

    OAuth2LoginOutcome outcome =
        authenticationService.loginWithOAuth2(
            "google", UUID.randomUUID().toString(), email, "Alice", "Dupont", null, "junit");

    assertThat(outcome.requiresLink()).isFalse();
    assertThat(outcome.tokens().user().id()).isEqualTo(existingUser.getId());

    User reloaded = userRepository.findById(existingUser.getId()).orElseThrow();
    assertThat(reloaded.isActive()).isTrue();

    assertThat(
            oauthAccountRepository.findAll().stream()
                .anyMatch(a -> a.getUser().getId().equals(existingUser.getId())))
        .isTrue();
  }

  @Test
  void resolvingUnknownLinkContextReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/oauth2/link-decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LinkDecisionRequest(UUID.randomUUID().toString(), LinkDecision.LINK))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void resolvingExpiredLinkContextThrowsAndDeletesContext() {
    String email = "google-expired-decision-" + UUID.randomUUID() + "@test.fr";
    User existingUser = saveLocalUser(email, true);
    String rawToken = UUID.randomUUID().toString();

    OauthLinkContext expiredContext =
        oauthLinkContextRepository.save(
            OauthLinkContext.builder()
                .tokenHash(tokenService.hashToken(rawToken))
                .targetUser(existingUser)
                .provider("google")
                .providerUserId(UUID.randomUUID().toString())
                .providerEmail(email)
                .firstName("Alice")
                .lastName("Dupont")
                .expiresAt(Instant.now().minusSeconds(60))
                .build());

    assertThatThrownBy(
            () ->
                authenticationService.resolveOAuth2LinkDecision(
                    new LinkDecisionRequest(rawToken, LinkDecision.LINK), "junit"))
        .isInstanceOf(RuntimeException.class);

    assertThat(oauthLinkContextRepository.findById(expiredContext.getId())).isEmpty();
  }
}
