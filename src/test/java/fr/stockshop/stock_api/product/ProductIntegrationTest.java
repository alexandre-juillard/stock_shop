package fr.stockshop.stock_api.product;

import static org.assertj.core.api.Assertions.assertThat;
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
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Fruits", "#11AA22");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name", "Pommes",
                            "categoryId", catId,
                            "quantityTypeId", typeId,
                            "baseUnitId", unitId))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.name").value("Pommes"))
        .andExpect(jsonPath("$.isVisible").value(true))
        .andExpect(jsonPath("$.category.id").value(catId.toString()))
        .andExpect(jsonPath("$.quantityType.code").value("weight"))
        .andExpect(jsonPath("$.baseUnit.code").value("kg"))
        .andExpect(jsonPath("$.baseUnit.isBaseUnit").value(true));

    Boolean visible =
        jdbcTemplate.queryForObject(
            "select is_visible from products where user_id = ? and name = ?",
            Boolean.class,
            user.getId(),
            "Pommes");
    assertThat(visible).isTrue();
  }

  @Test
  void createProductWithDuplicateNameReturnsConflict() throws Exception {
    String email = "products-dup-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Légumes", "#334455");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    insertProduct(user.getId(), catId, "Carottes", typeId, unitId, UUID.randomUUID(), true);

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name", "Carottes",
                            "categoryId", catId,
                            "quantityTypeId", typeId,
                            "baseUnitId", unitId))))
        .andExpect(status().isConflict());
  }

  @Test
  void createProductWithBaseUnitFromAnotherTypeReturnsBadRequest() throws Exception {
    String email = "products-mm-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Boissons", "#112244");
    UUID weightTypeId = weightTypeId();
    UUID liquidUnitId =
        jdbcTemplate.queryForObject(
            "select qu.id from quantity_units qu"
                + " join quantity_types qt on qt.id = qu.quantity_type_id"
                + " where qu.code = ? and qt.code = ?",
            UUID.class,
            "L",
            "liquid");

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name", "Farine",
                            "categoryId", catId,
                            "quantityTypeId", weightTypeId,
                            "baseUnitId", liquidUnitId))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createProductWithUnknownCategoryReturnsNotFound() throws Exception {
    String token = registerActivateAndLogin("products-cat404-" + UUID.randomUUID() + "@test.fr");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Poires",
                            "categoryId",
                            UUID.randomUUID(),
                            "quantityTypeId",
                            typeId,
                            "baseUnitId",
                            unitId))))
        .andExpect(status().isNotFound());
  }

  @Test
  void createProductWithUnknownQuantityTypeReturnsNotFound() throws Exception {
    String email = "products-type404-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Épicerie", "#556677");
    UUID unitId =
        jdbcTemplate.queryForObject(
            "select id from quantity_units where code = ?", UUID.class, "kg");

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Sucre",
                            "categoryId",
                            catId,
                            "quantityTypeId",
                            UUID.randomUUID(),
                            "baseUnitId",
                            unitId))))
        .andExpect(status().isNotFound());
  }

  @Test
  void createProductWithUnknownBaseUnitReturnsNotFound() throws Exception {
    String email = "products-unit404-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Surgelés", "#778899");
    UUID typeId = weightTypeId();

    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Petits pois",
                            "categoryId",
                            catId,
                            "quantityTypeId",
                            typeId,
                            "baseUnitId",
                            UUID.randomUUID()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void listProductsReturnsSortedAlphabeticallyAndExcludesOtherUsers() throws Exception {
    String email = "products-list-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Divers", "#AABBCC");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    insertProduct(user.getId(), catId, "Zucchini", typeId, unitId, UUID.randomUUID(), true);
    insertProduct(user.getId(), catId, "Abricot", typeId, unitId, UUID.randomUUID(), true);
    insertProduct(user.getId(), catId, "Melon", typeId, unitId, UUID.randomUUID(), true);

    // Produit d'un autre utilisateur — ne doit pas apparaître
    String otherEmail = "products-other-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(otherEmail);
    User other = userRepository.findByEmail(otherEmail).orElseThrow();
    UUID otherCatId = saveCategory(other, "Autre", "#112233");
    insertProduct(other.getId(), otherCatId, "Aaaaa", typeId, unitId, UUID.randomUUID(), true);

    mockMvc
        .perform(get("/api/products").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value("Abricot"))
        .andExpect(jsonPath("$[1].name").value("Melon"))
        .andExpect(jsonPath("$[2].name").value("Zucchini"));
  }

  @Test
  void listProductsFilteredByCategoryId() throws Exception {
    String email = "products-filter-cat-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID cat1 = saveCategory(user, "Féculents", "#123456");
    UUID cat2 = saveCategory(user, "Viandes", "#654321");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    insertProduct(user.getId(), cat1, "Pâtes", typeId, unitId, UUID.randomUUID(), true);
    insertProduct(user.getId(), cat1, "Riz", typeId, unitId, UUID.randomUUID(), true);
    insertProduct(user.getId(), cat2, "Poulet", typeId, unitId, UUID.randomUUID(), true);

    mockMvc
        .perform(
            get("/api/products")
                .header("Authorization", "Bearer " + token)
                .param("categoryId", cat1.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("Pâtes"))
        .andExpect(jsonPath("$[1].name").value("Riz"));
  }

  @Test
  void listProductsFilteredByVisible() throws Exception {
    String email = "products-filter-vis-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Conserves", "#ABCDEF");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    insertProduct(user.getId(), catId, "Haricots", typeId, unitId, UUID.randomUUID(), true);
    insertProduct(user.getId(), catId, "Lentilles", typeId, unitId, UUID.randomUUID(), false);

    mockMvc
        .perform(
            get("/api/products")
                .header("Authorization", "Bearer " + token)
                .param("visible", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Haricots"));
  }

  @Test
  void getProductByIdReturnsFullDetail() throws Exception {
    String email = "products-detail-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Fromages", "#FFCC00");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID prodId = UUID.randomUUID();
    insertProduct(user.getId(), catId, "Comté", typeId, unitId, prodId, true);

    mockMvc
        .perform(get("/api/products/" + prodId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(prodId.toString()))
        .andExpect(jsonPath("$.name").value("Comté"))
        .andExpect(jsonPath("$.category.id").value(catId.toString()))
        .andExpect(jsonPath("$.quantityType.code").value("weight"))
        .andExpect(jsonPath("$.baseUnit.code").value("kg"));
  }

  @Test
  void getProductByIdNotFoundReturns404() throws Exception {
    String token = registerActivateAndLogin("products-get404-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            get("/api/products/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateProductNameReturns200WithUpdatedPayload() throws Exception {
    String email = "products-update-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Céréales", "#332211");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID prodId = UUID.randomUUID();
    insertProduct(user.getId(), catId, "Avoine", typeId, unitId, prodId, true);

    mockMvc
        .perform(
            put("/api/products/" + prodId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Orge"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Orge"))
        .andExpect(jsonPath("$.id").value(prodId.toString()));
  }

  @Test
  void updateProductCategoryReturns200() throws Exception {
    String email = "products-update-cat-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID cat1 = saveCategory(user, "Ancien", "#001122");
    UUID cat2 = saveCategory(user, "Nouveau", "#334455");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID prodId = UUID.randomUUID();
    insertProduct(user.getId(), cat1, "Beurre", typeId, unitId, prodId, true);

    mockMvc
        .perform(
            put("/api/products/" + prodId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("categoryId", cat2))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category.id").value(cat2.toString()));
  }

  @Test
  void updateProductWithDuplicateNameReturnsConflict() throws Exception {
    String email = "products-update-dup-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Laitages", "#AACCBB");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    insertProduct(user.getId(), catId, "Yaourt", typeId, unitId, UUID.randomUUID(), true);
    UUID prodId = UUID.randomUUID();
    insertProduct(user.getId(), catId, "Crème", typeId, unitId, prodId, true);

    mockMvc
        .perform(
            put("/api/products/" + prodId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Yaourt"))))
        .andExpect(status().isConflict());
  }

  @Test
  void getProductOwnedByAnotherUserReturnsForbidden() throws Exception {
    String ownerEmail = "products-owner-get-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
    UUID catId = saveCategory(owner, "Privé", "#111111");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID prodId = UUID.randomUUID();
    insertProduct(owner.getId(), catId, "Secret", typeId, unitId, prodId, true);

    String outsiderToken =
        registerActivateAndLogin("products-outsider-get-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(get("/api/products/" + prodId).header("Authorization", "Bearer " + outsiderToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateProductOwnedByAnotherUserReturnsForbidden() throws Exception {
    String ownerEmail = "products-owner-put-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
    UUID catId = saveCategory(owner, "Mine", "#222222");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID prodId = UUID.randomUUID();
    insertProduct(owner.getId(), catId, "Produit protégé", typeId, unitId, prodId, true);

    String outsiderToken =
        registerActivateAndLogin("products-outsider-put-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            put("/api/products/" + prodId)
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Copie"))))
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

  private UUID saveCategory(User user, String name, String color) {
    return categoryRepository
        .save(Category.builder().user(user).name(name).color(color).build())
        .getId();
  }

  private void insertProduct(
      UUID userId,
      UUID catId,
      String name,
      UUID typeId,
      UUID unitId,
      UUID prodId,
      boolean visible) {
    jdbcTemplate.update(
        "insert into products (id, user_id, category_id, name, quantity_type_id, base_unit_id, is_visible)"
            + " values (?, ?, ?, ?, ?, ?, ?)",
        prodId,
        userId,
        catId,
        name,
        typeId,
        unitId,
        visible);
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
