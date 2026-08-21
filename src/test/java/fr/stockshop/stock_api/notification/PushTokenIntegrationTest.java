package fr.stockshop.stock_api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PushTokenIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void registerTokenCreatesEntryInDatabase() throws Exception {
    String token = registerActivateAndLogin("push-register-" + UUID.randomUUID() + "@test.fr");
    String deviceToken = "device-token-" + UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/push-tokens")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("token", deviceToken, "platform", "ANDROID"))))
        .andExpect(status().isNoContent());

    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from push_tokens where token = ?", Integer.class, deviceToken);
    String platform =
        jdbcTemplate.queryForObject(
            "select platform from push_tokens where token = ?", String.class, deviceToken);
    assertThat(count).isEqualTo(1);
    assertThat(platform).isEqualTo("ANDROID");
  }

  @Test
  void registerTokenReassignsOwnerWhenAlreadyRegisteredByAnotherUser() throws Exception {
    String user2Email = "push-owner2-" + UUID.randomUUID() + "@test.fr";
    String tokenUser1 = registerActivateAndLogin("push-owner1-" + UUID.randomUUID() + "@test.fr");
    String tokenUser2 = registerActivateAndLogin(user2Email);
    User user2 = userRepository.findByEmail(user2Email).orElseThrow();
    String deviceToken = "shared-device-" + UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/push-tokens")
                .header("Authorization", "Bearer " + tokenUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("token", deviceToken, "platform", "ANDROID"))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/push-tokens")
                .header("Authorization", "Bearer " + tokenUser2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("token", deviceToken, "platform", "IOS"))))
        .andExpect(status().isNoContent());

    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from push_tokens where token = ?", Integer.class, deviceToken);
    UUID ownerId =
        jdbcTemplate.queryForObject(
            "select user_id from push_tokens where token = ?", UUID.class, deviceToken);
    String platform =
        jdbcTemplate.queryForObject(
            "select platform from push_tokens where token = ?", String.class, deviceToken);
    assertThat(count).isEqualTo(1);
    assertThat(ownerId).isEqualTo(user2.getId());
    assertThat(platform).isEqualTo("IOS");
  }

  @Test
  void registerTokenRejectsBlankToken() throws Exception {
    String token = registerActivateAndLogin("push-blank-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/push-tokens")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("token", "", "platform", "ANDROID"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerTokenRejectsMissingPlatform() throws Exception {
    String token = registerActivateAndLogin("push-noplatform-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/push-tokens")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "device-x"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerTokenRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/push-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("token", "device-x", "platform", "ANDROID"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unregisterTokenDeletesRow() throws Exception {
    String token = registerActivateAndLogin("push-delete-" + UUID.randomUUID() + "@test.fr");
    String deviceToken = "device-to-delete-" + UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/push-tokens")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("token", deviceToken, "platform", "ANDROID"))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            delete("/api/push-tokens")
                .header("Authorization", "Bearer " + token)
                .param("token", deviceToken))
        .andExpect(status().isNoContent());

    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from push_tokens where token = ?", Integer.class, deviceToken);
    assertThat(count).isZero();
  }

  @Test
  void unregisterTokenReturnsNotFoundForUnknownToken() throws Exception {
    String token = registerActivateAndLogin("push-delete-404-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            delete("/api/push-tokens")
                .header("Authorization", "Bearer " + token)
                .param("token", "unknown-token-" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void unregisterTokenReturnsForbiddenWhenOwnedByAnotherUser() throws Exception {
    String tokenUser1 =
        registerActivateAndLogin("push-delete-403-1-" + UUID.randomUUID() + "@test.fr");
    String tokenUser2 =
        registerActivateAndLogin("push-delete-403-2-" + UUID.randomUUID() + "@test.fr");
    String deviceToken = "device-403-" + UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/push-tokens")
                .header("Authorization", "Bearer " + tokenUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("token", deviceToken, "platform", "ANDROID"))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            delete("/api/push-tokens")
                .header("Authorization", "Bearer " + tokenUser2)
                .param("token", deviceToken))
        .andExpect(status().isForbidden());
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
