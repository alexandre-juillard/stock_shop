package fr.stockshop.stock_api.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CategoryIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void getCategoriesReturnsOnlyCurrentUserCategoriesSortedAlphabetically() throws Exception {
    String ownerEmail = "categories-owner-" + UUID.randomUUID() + "@test.fr";
    String ownerAccessToken = registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();

    String otherEmail = "categories-other-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(otherEmail);
    User otherUser = userRepository.findByEmail(otherEmail).orElseThrow();

    categoryRepository.save(Category.builder().user(owner).name("fruits").color("#00AA00").build());
    categoryRepository.save(Category.builder().user(owner).name("epices").color("#AA5500").build());
    categoryRepository.save(
        Category.builder().user(otherUser).name("should-not-be-visible").color("#123456").build());

    mockMvc
        .perform(get("/api/categories").header("Authorization", "Bearer " + ownerAccessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("epices"))
        .andExpect(jsonPath("$[1].name").value("fruits"))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void createCategoryReturnsCreatedPayload() throws Exception {
    String accessToken =
        registerActivateAndLogin("categories-create-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/categories")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Laitages", "color", "#AABBCC"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.name").value("Laitages"))
        .andExpect(jsonPath("$.color").value("#AABBCC"));
  }

  @Test
  void createCategoryWithDuplicateNameIgnoringCaseReturnsConflict() throws Exception {
    String email = "categories-duplicate-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    User currentUser = userRepository.findByEmail(email).orElseThrow();

    categoryRepository.save(
        Category.builder().user(currentUser).name("Legumes").color("#ABCDEF").build());

    mockMvc
        .perform(
            post("/api/categories")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("name", "legumes", "color", "#000000"))))
        .andExpect(status().isConflict());
  }

  @Test
  void updateCategoryWithDuplicateNameIgnoringCaseReturnsConflict() throws Exception {
    String email = "categories-rename-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    User currentUser = userRepository.findByEmail(email).orElseThrow();

    Category first =
        categoryRepository.save(
            Category.builder().user(currentUser).name("Epicerie").color("#112233").build());
    Category second =
        categoryRepository.save(
            Category.builder().user(currentUser).name("Fruits").color("#445566").build());

    mockMvc
        .perform(
            put("/api/categories/" + second.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", first.getName().toLowerCase(), "color", "#778899"))))
        .andExpect(status().isConflict());
  }

  @Test
  void createCategoryWithInvalidColorReturnsBadRequest() throws Exception {
    String accessToken =
        registerActivateAndLogin("categories-color-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/categories")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("name", "Boissons", "color", "red"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteCategoryCascadesToRelatedData() throws Exception {
    String email = "categories-cascade-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    UUID userId = userRepository.findByEmail(email).orElseThrow().getId();

    String createResponse =
        mockMvc
            .perform(
                post("/api/categories")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("name", "A supprimer", "color", "#102030"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID categoryId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

    UUID quantityTypeId =
        jdbcTemplate.queryForObject(
            "select id from quantity_types where code = ?", UUID.class, "weight");
    UUID quantityUnitId =
        jdbcTemplate.queryForObject(
            "select id from quantity_units where code = ?", UUID.class, "kg");

    UUID productId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    UUID shoppingItemId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID recipeIngredientId = UUID.randomUUID();

    jdbcTemplate.update(
        "insert into products (id, user_id, category_id, name, quantity_type_id, base_unit_id, is_visible)"
            + " values (?, ?, ?, ?, ?, ?, ?)",
        productId,
        userId,
        categoryId,
        "Produit à supprimer",
        quantityTypeId,
        quantityUnitId,
        true);

    jdbcTemplate.update(
        "insert into stock_items (id, user_id, product_id, quantity) values (?, ?, ?, ?)",
        stockItemId,
        userId,
        productId,
        2.0);

    jdbcTemplate.update(
        "insert into shopping_list_items (id, user_id, product_id, is_checked, added_automatically)"
            + " values (?, ?, ?, ?, ?)",
        shoppingItemId,
        userId,
        productId,
        false,
        false);

    jdbcTemplate.update(
        "insert into recipes (id, user_id, name) values (?, ?, ?)", recipeId, userId, "Recette");

    jdbcTemplate.update(
        "insert into recipe_ingredients (id, recipe_id, product_id, quantity, unit_id)"
            + " values (?, ?, ?, ?, ?)",
        recipeIngredientId,
        recipeId,
        productId,
        1.0,
        quantityUnitId);

    mockMvc
        .perform(
            delete("/api/categories/" + categoryId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNoContent());

    assertThat(countById("categories", categoryId)).isZero();
    assertThat(countById("products", productId)).isZero();
    assertThat(countById("stock_items", stockItemId)).isZero();
    assertThat(countById("shopping_list_items", shoppingItemId)).isZero();
    assertThat(countById("recipes", recipeId)).isZero();
    assertThat(countById("recipe_ingredients", recipeIngredientId)).isZero();
  }

  @Test
  void updateCategoryOwnedByAnotherUserReturnsForbidden() throws Exception {
    String ownerEmail = "categories-owner-update-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();

    Category category =
        categoryRepository.save(
            Category.builder().user(owner).name("Privee").color("#111111").build());

    String outsiderToken =
        registerActivateAndLogin("categories-outsider-update-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            put("/api/categories/" + category.getId())
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Nouvelle", "color", "#222222"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteCategoryOwnedByAnotherUserReturnsForbidden() throws Exception {
    String ownerEmail = "categories-owner-delete-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();

    Category category =
        categoryRepository.save(
            Category.builder().user(owner).name("Privee delete").color("#333333").build());

    String outsiderToken =
        registerActivateAndLogin("categories-outsider-delete-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            delete("/api/categories/" + category.getId())
                .header("Authorization", "Bearer " + outsiderToken))
        .andExpect(status().isForbidden());
  }

  private long countById(String table, UUID id) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where id = ?", Long.class, id);
    return count == null ? 0 : count;
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
