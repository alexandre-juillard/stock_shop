package fr.stockshop.stock_api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.mail.EmailService;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.repository.UserRepository;
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
 * Vérifie que les messages de validation/erreur sont traduits dynamiquement selon l'en-tête {@code
 * Accept-Language}, que la langue est capturée à l'inscription, et qu'un utilisateur authentifié
 * peut modifier sa langue préférée via {@code PATCH /api/users/me/locale}.
 *
 * <p>{@link EmailService} est simulé ({@code @MockitoBean}) pour éviter toute tentative de
 * connexion SMTP réelle lors de l'inscription, hors périmètre de ce test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LocaleIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void validationMessagesAreTranslatedAccordingToAcceptLanguageHeader() throws Exception {
    String invalidPayload = objectMapper.writeValueAsString(Map.of("email", ""));

    mockMvc
        .perform(
            post("/api/auth/register")
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").value("Email is required"))
        .andExpect(jsonPath("$.message").value("Data validation error"));

    mockMvc
        .perform(
            post("/api/auth/register")
                .header("Accept-Language", "fr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").value("L'email est obligatoire"))
        .andExpect(jsonPath("$.message").value("Erreur de validation des données"));
  }

  @Test
  void registeringWithAcceptLanguageStoresPreferredLocaleOnAccount() throws Exception {
    String email = "locale-en-" + UUID.randomUUID() + "@test.fr";

    mockMvc
        .perform(
            post("/api/auth/register")
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.preferredLocale").value("en"));

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.getPreferredLocale()).isEqualTo("en");
  }

  @Test
  void updatingPreferredLocaleChangesStoredValue() throws Exception {
    String email = "locale-update-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(
            patch("/api/users/me/locale")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("locale", "en"))))
        .andExpect(status().isNoContent());

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.getPreferredLocale()).isEqualTo("en");
  }

  @Test
  void updatingToUnsupportedLocaleReturnsBadRequest() throws Exception {
    String email = "locale-invalid-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(
            patch("/api/users/me/locale")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("locale", "de"))))
        .andExpect(status().isBadRequest());

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.getPreferredLocale()).isNotEqualTo("de");
  }

  private String registerActivateAndLogin(String email) throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email)))
        .andExpect(status().isCreated());

    User user = userRepository.findByEmail(email).orElseThrow();
    user.setActive(true);
    userRepository.save(user);

    String body =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", email, "password", "Password123!"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readTree(body).get("accessToken").asText();
  }

  private String registerPayload(String email) throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "email", email,
            "password", "Password123!",
            "firstName", "Test",
            "lastName", "User"));
  }
}
