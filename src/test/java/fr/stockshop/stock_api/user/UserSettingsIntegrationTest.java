package fr.stockshop.stock_api.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserSettingsIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void updateSettingsWithValidValuesSavesAndReturnsProfile() throws Exception {
    String email = "settings-valid-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(
            put("/api/users/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("theme", "dark", "expirationAlertDays", 5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.theme").value("dark"))
        .andExpect(jsonPath("$.expirationAlertDays").value(5));
  }

  @Test
  void updateSettingsWithOnlyThemeLeavesExpirationAlertDaysUnchanged() throws Exception {
    String email = "settings-partial-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(
            put("/api/users/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .content(objectMapper.writeValueAsString(Map.of("theme", "dark"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.theme").value("dark"))
        .andExpect(jsonPath("$.expirationAlertDays").value(3));
  }

  @Test
  void updateSettingsWithInvalidThemeReturnsBadRequest() throws Exception {
    String email = "settings-bad-theme-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(
            put("/api/users/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .content(objectMapper.writeValueAsString(Map.of("theme", "invalid"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateSettingsWithZeroExpirationAlertDaysReturnsBadRequest() throws Exception {
    String email = "settings-bad-days-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(
            put("/api/users/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .content(objectMapper.writeValueAsString(Map.of("expirationAlertDays", 0))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateSettingsWithNegativeExpirationAlertDaysReturnsBadRequest() throws Exception {
    String email = "settings-neg-days-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(
            put("/api/users/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .content(objectMapper.writeValueAsString(Map.of("expirationAlertDays", -5))))
        .andExpect(status().isBadRequest());
  }

  private String registerActivateAndLogin(String email) throws Exception {
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
}
