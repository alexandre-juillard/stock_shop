package fr.stockshop.stock_api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Vérifie la consultation et la modification du profil (GET/PUT /api/users/me). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserProfileIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void gettingProfileWithValidTokenReturnsAccountInformation() throws Exception {
    String email = "profile-get-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.firstName").value("Alice"))
        .andExpect(jsonPath("$.lastName").value("Dupont"))
        .andExpect(jsonPath("$.theme").value("light"))
        .andExpect(jsonPath("$.expirationAlertDays").value(3))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void updatingFirstNameOnlyChangesFirstNameAndReturnsFullProfile() throws Exception {
    String email = "profile-update-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(
            put("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("firstName", "Bob"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Bob"))
        .andExpect(jsonPath("$.lastName").value("Dupont"))
        .andExpect(jsonPath("$.email").value(email));

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.getFirstName()).isEqualTo("Bob");
    assertThat(user.getLastName()).isEqualTo("Dupont");
  }

  @Test
  void updatingWithEmailAlreadyUsedByAnotherAccountReturnsConflictWithoutChangingProfile()
      throws Exception {
    String otherEmail = "profile-taken-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(otherEmail);

    String email = "profile-conflict-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(
            put("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", otherEmail, "firstName", "ShouldNotBeApplied"))))
        .andExpect(status().isConflict());

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.getFirstName()).isNotEqualTo("ShouldNotBeApplied");
  }

  @Test
  void updatingWithNoFieldsLeavesProfileUnchanged() throws Exception {
    String email = "profile-noop-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);

    mockMvc
        .perform(
            put("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.firstName").value("Alice"))
        .andExpect(jsonPath("$.lastName").value("Dupont"));
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
