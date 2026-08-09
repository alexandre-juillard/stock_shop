package fr.stockshop.stock_api.quantity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class QuantityReferenceIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void getQuantityTypesReturnsSeededTypesForAuthenticatedUser() throws Exception {
    String accessToken =
        registerActivateAndLogin("quantity-types-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(get("/api/quantity-types").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("liquid"))
        .andExpect(jsonPath("$[1].code").value("unit"))
        .andExpect(jsonPath("$[2].code").value("weight"))
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].label").isNotEmpty());
  }

  @Test
  void getUnitsByTypeReturnsUnitsSortedBySortOrder() throws Exception {
    String accessToken =
        registerActivateAndLogin("quantity-units-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            get("/api/quantity-types/weight/units")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("kg"))
        .andExpect(jsonPath("$[0].conversionFactor").value(1))
        .andExpect(jsonPath("$[0].isBaseUnit").value(true))
        .andExpect(jsonPath("$[1].code").value("hg"))
        .andExpect(jsonPath("$[2].code").value("dag"));
  }

  @Test
  void getUnitsByUnknownTypeReturnsNotFound() throws Exception {
    String accessToken =
        registerActivateAndLogin("quantity-missing-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            get("/api/quantity-types/unknown-type/units")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Type de quantité introuvable : unknown-type"));
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
