package fr.stockshop.stock_api.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
class RecipeIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void createRecipeReturnsCreatedPayloadWhenIngredientsReferenceExistingStock() throws Exception {
    String email = "recipes-create-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    UUID categoryId = saveCategory(user, "Recettes", "#112233");
    UUID weightTypeId = weightTypeId();
    UUID kgUnitId = kgUnitId(weightTypeId);
    UUID productId = UUID.randomUUID();
    insertProduct(user.getId(), categoryId, "Basilic", weightTypeId, kgUnitId, productId, true);
    insertStockItem(user.getId(), productId, 2.0);

    String responseBody =
        mockMvc
            .perform(
                post("/api/recipes")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "name",
                                "Pesto",
                                "ingredients",
                                List.of(
                                    Map.of(
                                        "productId",
                                        productId,
                                        "quantity",
                                        0.250,
                                        "unitId",
                                        kgUnitId))))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("Pesto"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID recipeId = UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());

    Integer recipeCount =
        jdbcTemplate.queryForObject(
            "select count(*) from recipes where id = ? and user_id = ? and name = ?",
            Integer.class,
            recipeId,
            user.getId(),
            "Pesto");
    Integer ingredientCount =
        jdbcTemplate.queryForObject(
            "select count(*) from recipe_ingredients where recipe_id = ? and product_id = ? and unit_id = ?",
            Integer.class,
            recipeId,
            productId,
            kgUnitId);

    assertThat(recipeCount).isEqualTo(1);
    assertThat(ingredientCount).isEqualTo(1);
  }

  @Test
  void createRecipeWithBlankNameReturnsBadRequest() throws Exception {
    String token = registerActivateAndLogin("recipes-blank-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "   "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
  }

  @Test
  void createRecipeWithNameLongerThan200ReturnsBadRequest() throws Exception {
    String token = registerActivateAndLogin("recipes-max-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "a".repeat(201)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
  }

  @Test
  void createRecipeWithProductOutOfStockReturnsBadRequest() throws Exception {
    String email = "recipes-outstock-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    UUID categoryId = saveCategory(user, "Recettes", "#445566");
    UUID weightTypeId = weightTypeId();
    UUID kgUnitId = kgUnitId(weightTypeId);
    UUID productId = UUID.randomUUID();
    insertProduct(user.getId(), categoryId, "Noisette", weightTypeId, kgUnitId, productId, true);

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Pate",
                            "ingredients",
                            List.of(
                                Map.of(
                                    "productId",
                                    productId,
                                    "quantity",
                                    1.0,
                                    "unitId",
                                    kgUnitId))))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRecipeWithIncompatibleUnitReturnsBadRequest() throws Exception {
    String email = "recipes-unit-mm-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    UUID categoryId = saveCategory(user, "Recettes", "#667788");
    UUID weightTypeId = weightTypeId();
    UUID kgUnitId = kgUnitId(weightTypeId);
    UUID literUnitId = literUnitId();
    UUID productId = UUID.randomUUID();
    insertProduct(user.getId(), categoryId, "Farine", weightTypeId, kgUnitId, productId, true);
    insertStockItem(user.getId(), productId, 4.0);

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Crepes",
                            "ingredients",
                            List.of(
                                Map.of(
                                    "productId",
                                    productId,
                                    "quantity",
                                    1.0,
                                    "unitId",
                                    literUnitId))))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listRecipesReturnsOnlyCurrentUserWithIngredientCountAndCreatedAt() throws Exception {
    String email = "recipes-list-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    UUID categoryId = saveCategory(user, "Recettes", "#778899");
    UUID weightTypeId = weightTypeId();
    UUID kgUnitId = kgUnitId(weightTypeId);
    UUID productId1 = UUID.randomUUID();
    UUID productId2 = UUID.randomUUID();
    insertProduct(user.getId(), categoryId, "Tomate", weightTypeId, kgUnitId, productId1, true);
    insertProduct(user.getId(), categoryId, "Mozzarella", weightTypeId, kgUnitId, productId2, true);

    UUID oldRecipeId =
        insertRecipe(user.getId(), "Salade caprese", Instant.parse("2026-08-20T10:00:00Z"));
    insertRecipeIngredient(oldRecipeId, productId1, kgUnitId, 0.300);
    insertRecipeIngredient(oldRecipeId, productId2, kgUnitId, 0.200);

    UUID recentRecipeId =
        insertRecipe(user.getId(), "Soupe tomate", Instant.parse("2026-08-21T10:00:00Z"));
    insertRecipeIngredient(recentRecipeId, productId1, kgUnitId, 0.800);

    String otherEmail = "recipes-list-other-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(otherEmail);
    User otherUser = userRepository.findByEmail(otherEmail).orElseThrow();
    insertRecipe(otherUser.getId(), "Recette privee", Instant.parse("2026-08-22T10:00:00Z"));

    mockMvc
        .perform(get("/api/recipes").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(recentRecipeId.toString()))
        .andExpect(jsonPath("$[0].name").value("Soupe tomate"))
        .andExpect(jsonPath("$[0].ingredientCount").value(1))
        .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
        .andExpect(jsonPath("$[1].id").value(oldRecipeId.toString()))
        .andExpect(jsonPath("$[1].ingredientCount").value(2));
  }

  @Test
  void getRecipeByIdReturnsFullIngredients() throws Exception {
    String email = "recipes-detail-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    UUID categoryId = saveCategory(user, "Recettes", "#8899AA");
    UUID weightTypeId = weightTypeId();
    UUID kgUnitId = kgUnitId(weightTypeId);
    UUID productId = UUID.randomUUID();
    insertProduct(
        user.getId(), categoryId, "Pomme de terre", weightTypeId, kgUnitId, productId, true);

    UUID recipeId = insertRecipe(user.getId(), "Puree", Instant.parse("2026-08-21T12:00:00Z"));
    insertRecipeIngredient(recipeId, productId, kgUnitId, 1.500);

    mockMvc
        .perform(get("/api/recipes/" + recipeId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(recipeId.toString()))
        .andExpect(jsonPath("$.name").value("Puree"))
        .andExpect(jsonPath("$.ingredients.length()").value(1))
        .andExpect(jsonPath("$.ingredients[0].product.id").value(productId.toString()))
        .andExpect(jsonPath("$.ingredients[0].product.name").value("Pomme de terre"))
        .andExpect(jsonPath("$.ingredients[0].quantity").value(1.5))
        .andExpect(jsonPath("$.ingredients[0].unit.id").value(kgUnitId.toString()))
        .andExpect(jsonPath("$.ingredients[0].unit.code").value("kg"))
        .andExpect(jsonPath("$.ingredients[0].unit.label").value("Kilogramme"));
  }

  @Test
  void getRecipeOwnedByAnotherUserReturnsForbidden() throws Exception {
    String ownerEmail = "recipes-owner-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();

    UUID recipeId = insertRecipe(owner.getId(), "Secrete", Instant.parse("2026-08-21T12:00:00Z"));

    String outsiderToken =
        registerActivateAndLogin("recipes-outsider-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(get("/api/recipes/" + recipeId).header("Authorization", "Bearer " + outsiderToken))
        .andExpect(status().isForbidden());
  }

  private UUID weightTypeId() {
    return jdbcTemplate.queryForObject(
        "select id from quantity_types where code = ?", UUID.class, "weight");
  }

  private UUID kgUnitId(UUID typeId) {
    return jdbcTemplate.queryForObject(
        "select id from quantity_units where code = ? and quantity_type_id = ?",
        UUID.class,
        "kg",
        typeId);
  }

  private UUID literUnitId() {
    return jdbcTemplate.queryForObject(
        "select qu.id from quantity_units qu "
            + "join quantity_types qt on qt.id = qu.quantity_type_id "
            + "where qu.code = ? and qt.code = ?",
        UUID.class,
        "L",
        "liquid");
  }

  private UUID saveCategory(User user, String name, String color) {
    return categoryRepository
        .save(Category.builder().user(user).name(name).color(color).build())
        .getId();
  }

  private void insertProduct(
      UUID userId,
      UUID categoryId,
      String name,
      UUID quantityTypeId,
      UUID unitId,
      UUID productId,
      boolean visible) {
    jdbcTemplate.update(
        "insert into products (id, user_id, category_id, name, quantity_type_id, base_unit_id, is_visible)"
            + " values (?, ?, ?, ?, ?, ?, ?)",
        productId,
        userId,
        categoryId,
        name,
        quantityTypeId,
        unitId,
        visible);
  }

  private void insertStockItem(UUID userId, UUID productId, double quantity) {
    jdbcTemplate.update(
        "insert into stock_items (id, user_id, product_id, quantity, created_at, updated_at)"
            + " values (?, ?, ?, ?, now(), now())",
        UUID.randomUUID(),
        userId,
        productId,
        quantity);
  }

  private UUID insertRecipe(UUID userId, String name, Instant createdAt) {
    UUID recipeId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into recipes (id, user_id, name, created_at, updated_at) values (?, ?, ?, ?, ?)",
        recipeId,
        userId,
        name,
        Timestamp.from(createdAt),
        Timestamp.from(createdAt));
    return recipeId;
  }

  private void insertRecipeIngredient(UUID recipeId, UUID productId, UUID unitId, double quantity) {
    jdbcTemplate.update(
        "insert into recipe_ingredients (id, recipe_id, product_id, quantity, unit_id) values (?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        recipeId,
        productId,
        quantity,
        unitId);
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
