package fr.stockshop.stock_api.shoppinglist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.math.BigDecimal;
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
class ShoppingListIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void getShoppingListReturnsGroupedByCategoryAndSortedItemsWithRequiredFields() throws Exception {
    String email = "shopping-list-group-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID typeId = weightTypeId();
    UUID kgUnitId = kgUnitId(typeId);

    UUID legumesId = saveCategory(user, "Legumes", "#AA5500");
    UUID fruitsId = saveCategory(user, "Fruits", "#22AA22");

    UUID carotteId = insertProduct(user.getId(), legumesId, "Carotte", typeId, kgUnitId, true);
    UUID pommeId = insertProduct(user.getId(), fruitsId, "Pomme", typeId, kgUnitId, true);
    UUID bananeId = insertProduct(user.getId(), fruitsId, "Banane", typeId, kgUnitId, true);

    insertShoppingListItem(user.getId(), carotteId, false, null, null, false);
    insertShoppingListItem(user.getId(), pommeId, true, new BigDecimal("1.500"), kgUnitId, false);
    insertShoppingListItem(user.getId(), bananeId, false, null, null, true);

    mockMvc
        .perform(get("/api/shopping-list").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].category.name").value("Fruits"))
        .andExpect(jsonPath("$[0].category.color").value("#22AA22"))
        .andExpect(jsonPath("$[0].items.length()").value(2))
        .andExpect(jsonPath("$[0].items[0].product.name").value("Banane"))
        .andExpect(jsonPath("$[0].items[0].addedAutomatically").value(true))
        .andExpect(jsonPath("$[0].items[0].isChecked").value(false))
        .andExpect(jsonPath("$[0].items[0].addedAt").isNotEmpty())
        .andExpect(jsonPath("$[0].items[1].product.name").value("Pomme"))
        .andExpect(jsonPath("$[0].items[1].isChecked").value(true))
        .andExpect(jsonPath("$[0].items[1].checkedQuantity").value(1.5))
        .andExpect(jsonPath("$[0].items[1].checkedUnit.id").value(kgUnitId.toString()))
        .andExpect(jsonPath("$[0].items[1].checkedUnit.code").value("kg"))
        .andExpect(jsonPath("$[0].items[1].checkedUnit.label").value("Kilogramme"))
        .andExpect(jsonPath("$[0].items[1].addedAutomatically").value(false))
        .andExpect(jsonPath("$[0].items[1].addedAt").isNotEmpty())
        .andExpect(jsonPath("$[1].category.name").value("Legumes"))
        .andExpect(jsonPath("$[1].category.color").value("#AA5500"))
        .andExpect(jsonPath("$[1].items.length()").value(1))
        .andExpect(jsonPath("$[1].items[0].product.id").value(carotteId.toString()))
        .andExpect(jsonPath("$[1].items[0].product.name").value("Carotte"));
  }

  @Test
  void getShoppingListExcludesCategoriesWithoutItems() throws Exception {
    String email = "shopping-list-empty-cat-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    UUID usedCategoryId = saveCategory(user, "Fruits", "#00AA00");
    saveCategory(user, "SansItem", "#0000AA");

    UUID productId = insertProduct(user.getId(), usedCategoryId, "Kiwi", typeId, unitId, true);
    insertShoppingListItem(user.getId(), productId, false, null, null, false);

    mockMvc
        .perform(get("/api/shopping-list").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].category.name").value("Fruits"));
  }

  @Test
  void getShoppingListExcludesInvisibleProducts() throws Exception {
    String email = "shopping-list-visible-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    UUID categoryId = saveCategory(user, "Fruits", "#00AA00");

    UUID visibleProductId = insertProduct(user.getId(), categoryId, "Poire", typeId, unitId, true);
    UUID hiddenProductId =
        insertProduct(user.getId(), categoryId, "ProduitCache", typeId, unitId, false);

    insertShoppingListItem(user.getId(), visibleProductId, false, null, null, false);
    insertShoppingListItem(user.getId(), hiddenProductId, false, null, null, false);

    mockMvc
        .perform(get("/api/shopping-list").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].items.length()").value(1))
        .andExpect(jsonPath("$[0].items[0].product.id").value(visibleProductId.toString()))
        .andExpect(jsonPath("$[0].items[0].product.name").value("Poire"));

    Integer countVisible =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where user_id = ?",
            Integer.class,
            user.getId());
    assertThat(countVisible).isEqualTo(2);
  }

  @Test
  void getShoppingListOnlyReturnsCurrentUserItems() throws Exception {
    String ownerEmail = "shopping-owner-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();

    String requesterEmail = "shopping-requester-" + UUID.randomUUID() + "@test.fr";
    String requesterToken = registerActivateAndLogin(requesterEmail);
    User requester = userRepository.findByEmail(requesterEmail).orElseThrow();

    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    UUID ownerCategoryId = saveCategory(owner, "OwnerCat", "#111111");
    UUID requesterCategoryId = saveCategory(requester, "RequesterCat", "#222222");

    UUID ownerProductId =
        insertProduct(owner.getId(), ownerCategoryId, "ProduitOwner", typeId, unitId, true);
    UUID requesterProductId =
        insertProduct(
            requester.getId(), requesterCategoryId, "ProduitRequester", typeId, unitId, true);

    insertShoppingListItem(owner.getId(), ownerProductId, false, null, null, false);
    insertShoppingListItem(requester.getId(), requesterProductId, false, null, null, false);

    mockMvc
        .perform(get("/api/shopping-list").header("Authorization", "Bearer " + requesterToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].items.length()").value(1))
        .andExpect(jsonPath("$[0].items[0].product.id").value(requesterProductId.toString()))
        .andExpect(jsonPath("$[0].items[0].product.name").value("ProduitRequester"));
  }

  @Test
  void getShoppingListRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/shopping-list")).andExpect(status().isUnauthorized());
  }

  @Test
  void addShoppingListItemCreatesManualUncheckedItemAndReturns201() throws Exception {
    String email = "shopping-add-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID categoryId = saveCategory(user, "Fruits", "#11AA11");
    UUID productId = insertProduct(user.getId(), categoryId, "Pomme", typeId, unitId, true);

    mockMvc
        .perform(
            post("/api/shopping-list/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("productId", productId))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.product.id").value(productId.toString()))
        .andExpect(jsonPath("$.product.name").value("Pomme"))
        .andExpect(jsonPath("$.isChecked").value(false))
        .andExpect(jsonPath("$.checkedQuantity").doesNotExist())
        .andExpect(jsonPath("$.checkedUnit").doesNotExist())
        .andExpect(jsonPath("$.addedAutomatically").value(false))
        .andExpect(jsonPath("$.addedAt").isNotEmpty());

    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where user_id = ? and product_id = ? "
                + "and is_checked = false and added_automatically = false",
            Integer.class,
            user.getId(),
            productId);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void addShoppingListItemReturnsConflictWhenProductAlreadyInList() throws Exception {
    String email = "shopping-add-dup-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID categoryId = saveCategory(user, "Fruits", "#11AA11");
    UUID productId = insertProduct(user.getId(), categoryId, "Banane", typeId, unitId, true);
    insertShoppingListItem(user.getId(), productId, false, null, null, false);

    mockMvc
        .perform(
            post("/api/shopping-list/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("productId", productId))))
        .andExpect(status().isConflict());
  }

  @Test
  void addShoppingListItemReturnsNotFoundForInvisibleProduct() throws Exception {
    String email = "shopping-add-hidden-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID categoryId = saveCategory(user, "Fruits", "#11AA11");
    UUID hiddenProductId =
        insertProduct(user.getId(), categoryId, "ProduitCache", typeId, unitId, false);

    mockMvc
        .perform(
            post("/api/shopping-list/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("productId", hiddenProductId))))
        .andExpect(status().isNotFound());
  }

  @Test
  void addShoppingListItemRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/shopping-list/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("productId", UUID.randomUUID()))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteShoppingListItemDeletesOwnedItem() throws Exception {
    String email = "shopping-delete-item-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID categoryId = saveCategory(user, "Legumes", "#AA5500");
    UUID productId = insertProduct(user.getId(), categoryId, "Carotte", typeId, unitId, true);
    UUID shoppingListItemId =
        insertShoppingListItem(user.getId(), productId, false, null, null, false);

    mockMvc
        .perform(
            delete("/api/shopping-list/items/{id}", shoppingListItemId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where id = ?",
            Integer.class,
            shoppingListItemId);
    assertThat(count).isZero();
  }

  @Test
  void deleteShoppingListItemReturnsNotFoundWhenUnknown() throws Exception {
    String email = "shopping-delete-item-404-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);

    mockMvc
        .perform(
            delete("/api/shopping-list/items/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteShoppingListItemReturnsForbiddenWhenOwnedByAnotherUser() throws Exception {
    String ownerEmail = "shopping-delete-owner-" + UUID.randomUUID() + "@test.fr";
    String ownerToken = registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();

    String requesterEmail = "shopping-delete-requester-" + UUID.randomUUID() + "@test.fr";
    String requesterToken = registerActivateAndLogin(requesterEmail);

    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID categoryId = saveCategory(owner, "OwnerCat", "#444444");
    UUID productId = insertProduct(owner.getId(), categoryId, "ProduitOwner", typeId, unitId, true);
    UUID shoppingListItemId =
        insertShoppingListItem(owner.getId(), productId, false, null, null, false);

    mockMvc
        .perform(
            delete("/api/shopping-list/items/{id}", shoppingListItemId)
                .header("Authorization", "Bearer " + requesterToken))
        .andExpect(status().isForbidden());

    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where id = ?",
            Integer.class,
            shoppingListItemId);
    assertThat(count).isEqualTo(1);

    // Utilisé uniquement pour garantir que le token owner est bien valide pour ce scénario.
    mockMvc
        .perform(get("/api/shopping-list").header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk());
  }

  @Test
  void clearShoppingListDeletesOnlyCurrentUserItems() throws Exception {
    String ownerEmail = "shopping-clear-owner-" + UUID.randomUUID() + "@test.fr";
    String ownerToken = registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();

    String otherEmail = "shopping-clear-other-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(otherEmail);
    User other = userRepository.findByEmail(otherEmail).orElseThrow();

    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID ownerCategoryId = saveCategory(owner, "Fruits", "#11AA11");
    UUID otherCategoryId = saveCategory(other, "Legumes", "#AA5500");

    UUID ownerProductId1 =
        insertProduct(owner.getId(), ownerCategoryId, "Pomme", typeId, unitId, true);
    UUID ownerProductId2 =
        insertProduct(owner.getId(), ownerCategoryId, "Banane", typeId, unitId, true);
    UUID otherProductId =
        insertProduct(other.getId(), otherCategoryId, "Carotte", typeId, unitId, true);

    insertShoppingListItem(owner.getId(), ownerProductId1, false, null, null, false);
    insertShoppingListItem(owner.getId(), ownerProductId2, false, null, null, false);
    insertShoppingListItem(other.getId(), otherProductId, false, null, null, false);

    mockMvc
        .perform(delete("/api/shopping-list").header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isNoContent());

    Integer ownerCount =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where user_id = ?",
            Integer.class,
            owner.getId());
    Integer otherCount =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where user_id = ?",
            Integer.class,
            other.getId());
    assertThat(ownerCount).isZero();
    assertThat(otherCount).isEqualTo(1);
  }

  @Test
  void clearShoppingListRequiresAuthentication() throws Exception {
    mockMvc.perform(delete("/api/shopping-list")).andExpect(status().isUnauthorized());
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

  private UUID insertProduct(
      UUID userId,
      UUID categoryId,
      String name,
      UUID quantityTypeId,
      UUID baseUnitId,
      boolean visible) {
    UUID productId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into products (id, user_id, category_id, name, quantity_type_id, base_unit_id, is_visible)"
            + " values (?, ?, ?, ?, ?, ?, ?)",
        productId,
        userId,
        categoryId,
        name,
        quantityTypeId,
        baseUnitId,
        visible);
    return productId;
  }

  private UUID insertShoppingListItem(
      UUID userId,
      UUID productId,
      boolean isChecked,
      BigDecimal checkedQuantity,
      UUID checkedUnitId,
      boolean addedAutomatically) {
    UUID shoppingListItemId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into shopping_list_items (id, user_id, product_id, is_checked, checked_quantity, checked_unit_id, added_automatically, added_at)"
            + " values (?, ?, ?, ?, ?, ?, ?, now())",
        shoppingListItemId,
        userId,
        productId,
        isChecked,
        checkedQuantity,
        checkedUnitId,
        addedAutomatically);
    return shoppingListItemId;
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
