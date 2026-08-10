package fr.stockshop.stock_api.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.category.entity.Category;
import fr.stockshop.stock_api.category.repository.CategoryRepository;
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
class ProductIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void createProductReturnsCreatedPayloadWithNestedObjects() throws Exception {
    String email = "products-create-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    User currentUser = userRepository.findByEmail(email).orElseThrow();
    Category category =
        categoryRepository.save(
            Category.builder().user(currentUser).name("Fruits").color("#11AA22").build());

    UUID quantityTypeId =
        jdbcTemplate.queryForObject(
            "select id from quantity_types where code = ?", UUID.class, "weight");
    UUID baseUnitId =
        jdbcTemplate.queryForObject(
            "select id from quantity_units where code = ? and quantity_type_id = ?",
            UUID.class,
            "kg",
            quantityTypeId);

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Pommes",
                            "categoryId",
                            category.getId(),
                            "quantityTypeId",
                            quantityTypeId,
                            "baseUnitId",
                            baseUnitId))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.name").value("Pommes"))
        .andExpect(jsonPath("$.isVisible").value(true))
        .andExpect(jsonPath("$.category.id").value(category.getId().toString()))
        .andExpect(jsonPath("$.category.name").value("Fruits"))
        .andExpect(jsonPath("$.category.color").value("#11AA22"))
        .andExpect(jsonPath("$.quantityType.id").value(quantityTypeId.toString()))
        .andExpect(jsonPath("$.quantityType.code").value("weight"))
        .andExpect(jsonPath("$.quantityType.label").value("Poids"))
        .andExpect(jsonPath("$.baseUnit.id").value(baseUnitId.toString()))
        .andExpect(jsonPath("$.baseUnit.code").value("kg"))
        .andExpect(jsonPath("$.baseUnit.label").value("Kilogramme"))
        .andExpect(jsonPath("$.baseUnit.conversionFactor").value(1))
        .andExpect(jsonPath("$.baseUnit.isBaseUnit").value(true));

    Boolean isVisible =
        jdbcTemplate.queryForObject(
            "select is_visible from products where user_id = ? and name = ?",
            Boolean.class,
            currentUser.getId(),
            "Pommes");
    assertThat(isVisible).isTrue();
  }

  @Test
  void createProductWithDuplicateNameForSameUserReturnsConflict() throws Exception {
    String email = "products-duplicate-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    User currentUser = userRepository.findByEmail(email).orElseThrow();
    Category category =
        categoryRepository.save(
            Category.builder().user(currentUser).name("Légumes").color("#334455").build());

    UUID quantityTypeId =
        jdbcTemplate.queryForObject(
            "select id from quantity_types where code = ?", UUID.class, "weight");
    UUID baseUnitId =
        jdbcTemplate.queryForObject(
            "select id from quantity_units where code = ? and quantity_type_id = ?",
            UUID.class,
            "kg",
            quantityTypeId);

    jdbcTemplate.update(
        "insert into products (id, user_id, category_id, name, quantity_type_id, base_unit_id, is_visible)"
            + " values (?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        currentUser.getId(),
        category.getId(),
        "Carottes",
        quantityTypeId,
        baseUnitId,
        true);

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Carottes",
                            "categoryId",
                            category.getId(),
                            "quantityTypeId",
                            quantityTypeId,
                            "baseUnitId",
                            baseUnitId))))
        .andExpect(status().isConflict());
  }

  @Test
  void createProductWithBaseUnitFromAnotherTypeReturnsBadRequest() throws Exception {
    String email = "products-mismatch-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    User currentUser = userRepository.findByEmail(email).orElseThrow();
    Category category =
        categoryRepository.save(
            Category.builder().user(currentUser).name("Boissons").color("#112244").build());

    UUID quantityTypeId =
        jdbcTemplate.queryForObject(
            "select id from quantity_types where code = ?", UUID.class, "weight");
    UUID liquidBaseUnitId =
        jdbcTemplate.queryForObject(
            "select qu.id from quantity_units qu join quantity_types qt on qt.id = qu.quantity_type_id"
                + " where qu.code = ? and qt.code = ?",
            UUID.class,
            "L",
            "liquid");

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Farine",
                            "categoryId",
                            category.getId(),
                            "quantityTypeId",
                            quantityTypeId,
                            "baseUnitId",
                            liquidBaseUnitId))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void createProductWithUnknownCategoryReturnsNotFound() throws Exception {
    String accessToken =
        registerActivateAndLogin("products-category-" + UUID.randomUUID() + "@test.fr");
    UUID quantityTypeId =
        jdbcTemplate.queryForObject(
            "select id from quantity_types where code = ?", UUID.class, "weight");
    UUID baseUnitId =
        jdbcTemplate.queryForObject(
            "select id from quantity_units where code = ? and quantity_type_id = ?",
            UUID.class,
            "kg",
            quantityTypeId);

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Poires",
                            "categoryId",
                            UUID.randomUUID(),
                            "quantityTypeId",
                            quantityTypeId,
                            "baseUnitId",
                            baseUnitId))))
        .andExpect(status().isNotFound());
  }

  @Test
  void createProductWithUnknownQuantityTypeReturnsNotFound() throws Exception {
    String email = "products-type-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    User currentUser = userRepository.findByEmail(email).orElseThrow();
    Category category =
        categoryRepository.save(
            Category.builder().user(currentUser).name("Épicerie").color("#556677").build());
    UUID baseUnitId =
        jdbcTemplate.queryForObject(
            "select id from quantity_units where code = ?", UUID.class, "kg");

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Sucre",
                            "categoryId",
                            category.getId(),
                            "quantityTypeId",
                            UUID.randomUUID(),
                            "baseUnitId",
                            baseUnitId))))
        .andExpect(status().isNotFound());
  }

  @Test
  void createProductWithUnknownBaseUnitReturnsNotFound() throws Exception {
    String email = "products-unit-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    User currentUser = userRepository.findByEmail(email).orElseThrow();
    Category category =
        categoryRepository.save(
            Category.builder().user(currentUser).name("Surgelés").color("#778899").build());
    UUID quantityTypeId =
        jdbcTemplate.queryForObject(
            "select id from quantity_types where code = ?", UUID.class, "weight");

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Petits pois",
                            "categoryId",
                            category.getId(),
                            "quantityTypeId",
                            quantityTypeId,
                            "baseUnitId",
                            UUID.randomUUID()))))
        .andExpect(status().isNotFound());
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
