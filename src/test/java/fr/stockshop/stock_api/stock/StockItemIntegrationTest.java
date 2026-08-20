package fr.stockshop.stock_api.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.time.LocalDate;
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
class StockItemIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void listStockItemsReturnsSortedByCategoryThenNameAndExcludesInvisibleProducts()
      throws Exception {
    String email = "stock-list-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    UUID catB = saveCategory(user, "Boissons", "#111111");
    UUID catA = saveCategory(user, "Aliments", "#222222");

    UUID productZ = insertProduct(user.getId(), catA, "Zeste", typeId, unitId, true);
    UUID productA = insertProduct(user.getId(), catA, "Avoine", typeId, unitId, true);
    UUID productHidden = insertProduct(user.getId(), catA, "Caché", typeId, unitId, false);
    UUID productSoda = insertProduct(user.getId(), catB, "Soda", typeId, unitId, true);

    insertStockItem(user.getId(), productZ, BigDecimal.TEN, null, null);
    insertStockItem(user.getId(), productA, BigDecimal.TEN, null, null);
    insertStockItem(user.getId(), productHidden, BigDecimal.TEN, null, null);
    insertStockItem(user.getId(), productSoda, BigDecimal.TEN, null, null);

    mockMvc
        .perform(get("/api/stock-items").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].product.name").value("Avoine"))
        .andExpect(jsonPath("$[1].product.name").value("Zeste"))
        .andExpect(jsonPath("$[2].product.name").value("Soda"));
  }

  @Test
  void listStockItemsComputesStatusForEachCase() throws Exception {
    String email = "stock-status-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    jdbcTemplate.update("update users set expiration_alert_days = ? where id = ?", 3, user.getId());
    UUID catId = saveCategory(user, "Statuts", "#333333");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    UUID okProduct = insertProduct(user.getId(), catId, "OkItem", typeId, unitId, true);
    UUID lowProduct = insertProduct(user.getId(), catId, "LowItem", typeId, unitId, true);
    UUID expiringProduct = insertProduct(user.getId(), catId, "ExpiringItem", typeId, unitId, true);
    UUID expiredProduct = insertProduct(user.getId(), catId, "ExpiredItem", typeId, unitId, true);

    insertStockItem(user.getId(), okProduct, BigDecimal.TEN, new BigDecimal("2"), null);
    insertStockItem(user.getId(), lowProduct, new BigDecimal("1"), new BigDecimal("2"), null);
    insertStockItem(
        user.getId(), expiringProduct, BigDecimal.TEN, null, LocalDate.now().plusDays(1));
    insertStockItem(
        user.getId(), expiredProduct, BigDecimal.TEN, null, LocalDate.now().minusDays(1));

    mockMvc
        .perform(get("/api/stock-items").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.product.name=='OkItem')].status").value("ok"))
        .andExpect(jsonPath("$[?(@.product.name=='LowItem')].status").value("low"))
        .andExpect(jsonPath("$[?(@.product.name=='ExpiringItem')].status").value("expiring"))
        .andExpect(jsonPath("$[?(@.product.name=='ExpiredItem')].status").value("expired"))
        .andExpect(jsonPath("$[?(@.product.name=='ExpiredItem')].needsQuantityUpdate").value(true))
        .andExpect(jsonPath("$[?(@.product.name=='OkItem')].needsQuantityUpdate").value(false));
  }

  @Test
  void listStockItemsWithExpiringSoonParamReturnsOnlyExpiringOrExpired() throws Exception {
    String email = "stock-filter-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    jdbcTemplate.update("update users set expiration_alert_days = ? where id = ?", 3, user.getId());
    UUID catId = saveCategory(user, "Filtre", "#444444");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    UUID okProduct = insertProduct(user.getId(), catId, "Ok", typeId, unitId, true);
    UUID expiredProduct = insertProduct(user.getId(), catId, "Perime", typeId, unitId, true);

    insertStockItem(user.getId(), okProduct, BigDecimal.TEN, null, null);
    insertStockItem(
        user.getId(), expiredProduct, BigDecimal.TEN, null, LocalDate.now().minusDays(1));

    mockMvc
        .perform(
            get("/api/stock-items")
                .header("Authorization", "Bearer " + token)
                .param("expiringSoon", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].product.name").value("Perime"));
  }

  @Test
  void expiringSoonEndpointReturnsItemsSortedByExpirationDateAscending() throws Exception {
    String email = "stock-expiring-soon-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    jdbcTemplate.update("update users set expiration_alert_days = ? where id = ?", 5, user.getId());
    UUID catId = saveCategory(user, "Bientot", "#555555");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);

    UUID okProduct = insertProduct(user.getId(), catId, "Ok", typeId, unitId, true);
    UUID soonProduct = insertProduct(user.getId(), catId, "DansTroisJours", typeId, unitId, true);
    UUID expiredProduct = insertProduct(user.getId(), catId, "DejaPerime", typeId, unitId, true);

    insertStockItem(user.getId(), okProduct, BigDecimal.TEN, null, LocalDate.now().plusDays(30));
    insertStockItem(user.getId(), soonProduct, BigDecimal.TEN, null, LocalDate.now().plusDays(3));
    insertStockItem(
        user.getId(), expiredProduct, BigDecimal.TEN, null, LocalDate.now().minusDays(2));

    mockMvc
        .perform(get("/api/stock-items/expiring-soon").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].product.name").value("DejaPerime"))
        .andExpect(jsonPath("$[1].product.name").value("DansTroisJours"));
  }

  @Test
  void listStockItemsOnlyReturnsCurrentUserItems() throws Exception {
    String ownerEmail = "stock-owner-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
    UUID catId = saveCategory(owner, "Prive", "#666666");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(owner.getId(), catId, "Secret", typeId, unitId, true);
    insertStockItem(owner.getId(), productId, BigDecimal.TEN, null, null);

    String outsiderToken =
        registerActivateAndLogin("stock-outsider-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(get("/api/stock-items").header("Authorization", "Bearer " + outsiderToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void createStockItemReturnsCreatedWithNestedProduct() throws Exception {
    String email = "stock-create-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Fruits", "#111111");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Pomme", typeId, unitId, true);

    mockMvc
        .perform(
            post("/api/stock-items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("productId", productId, "quantity", 5))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.product.id").value(productId.toString()))
        .andExpect(jsonPath("$.product.name").value("Pomme"))
        .andExpect(jsonPath("$.quantity").value(5))
        .andExpect(jsonPath("$.status").value("ok"));

    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from stock_items where user_id = ? and product_id = ?",
            Integer.class,
            user.getId(),
            productId);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void createStockItemWithDuplicateProductReturnsConflict() throws Exception {
    String email = "stock-dup-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Legumes", "#222222");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Carotte", typeId, unitId, true);
    insertStockItem(user.getId(), productId, BigDecimal.TEN, null, null);

    mockMvc
        .perform(
            post("/api/stock-items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("productId", productId, "quantity", 3))))
        .andExpect(status().isConflict());
  }

  @Test
  void createStockItemWithPastExpirationDateReturnsBadRequest() throws Exception {
    String email = "stock-past-exp-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Viandes", "#333333");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Poulet", typeId, unitId, true);

    mockMvc
        .perform(
            post("/api/stock-items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "productId",
                            productId,
                            "quantity",
                            2,
                            "expirationDate",
                            LocalDate.now().minusDays(1).toString()))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createStockItemWithNegativeThresholdReturnsBadRequest() throws Exception {
    String email = "stock-neg-threshold-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Poissons", "#444444");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Saumon", typeId, unitId, true);

    mockMvc
        .perform(
            post("/api/stock-items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("productId", productId, "quantity", 2, "lowThreshold", -1))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createStockItemWithQuantityBelowThresholdAddsProductToShoppingList() throws Exception {
    String email = "stock-shopping-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Epicerie", "#555555");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Farine", typeId, unitId, true);

    mockMvc
        .perform(
            post("/api/stock-items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "productId", productId,
                            "quantity", 1,
                            "lowThreshold", 2))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("low"));

    Integer shoppingCount =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where user_id = ? and product_id = ? and added_automatically = true",
            Integer.class,
            user.getId(),
            productId);
    assertThat(shoppingCount).isEqualTo(1);
  }

  @Test
  void createStockItemWithQuantityAboveThresholdDoesNotAddToShoppingList() throws Exception {
    String email = "stock-no-shopping-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Cereales", "#666666");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Avoine", typeId, unitId, true);

    mockMvc
        .perform(
            post("/api/stock-items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "productId", productId,
                            "quantity", 10,
                            "lowThreshold", 2))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ok"));

    Integer shoppingCount =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where user_id = ? and product_id = ?",
            Integer.class,
            user.getId(),
            productId);
    assertThat(shoppingCount).isZero();
  }

  @Test
  void createStockItemOwnedByAnotherUserReturnsForbidden() throws Exception {
    String ownerEmail = "stock-create-owner-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
    UUID catId = saveCategory(owner, "Prive2", "#777777");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(owner.getId(), catId, "Confidentiel", typeId, unitId, true);

    String outsiderToken =
        registerActivateAndLogin("stock-create-outsider-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/stock-items")
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("productId", productId, "quantity", 1))))
        .andExpect(status().isForbidden());
  }

  @Test
  void createStockItemWithUnknownProductReturnsNotFound() throws Exception {
    String token = registerActivateAndLogin("stock-create-404-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            post("/api/stock-items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("productId", UUID.randomUUID(), "quantity", 1))))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateQuantityReturns200WithUpdatedQuantity() throws Exception {
    String email = "stock-qty-ok-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Fruits", "#101010");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Banane", typeId, unitId, true);
    UUID stockItemId = insertStockItem(user.getId(), productId, BigDecimal.TEN, null, null);

    mockMvc
        .perform(
            patch("/api/stock-items/" + stockItemId + "/quantity")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", 4))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(stockItemId.toString()))
        .andExpect(jsonPath("$.quantity").value(4));

    java.math.BigDecimal storedQuantity =
        jdbcTemplate.queryForObject(
            "select quantity from stock_items where id = ?",
            java.math.BigDecimal.class,
            stockItemId);
    assertThat(storedQuantity).isEqualByComparingTo("4");
  }

  @Test
  void updateQuantityWithNegativeValueReturnsBadRequest() throws Exception {
    String email = "stock-qty-neg-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Legumes", "#202020");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Poireau", typeId, unitId, true);
    UUID stockItemId = insertStockItem(user.getId(), productId, BigDecimal.TEN, null, null);

    mockMvc
        .perform(
            patch("/api/stock-items/" + stockItemId + "/quantity")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", -1))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateQuantityBelowOrEqualThresholdAddsToShoppingList() throws Exception {
    String email = "stock-qty-shopping-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Epicerie", "#303030");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Pates", typeId, unitId, true);
    UUID stockItemId =
        insertStockItem(user.getId(), productId, BigDecimal.TEN, new BigDecimal("2"), null);

    mockMvc
        .perform(
            patch("/api/stock-items/" + stockItemId + "/quantity")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", 2))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("low"));

    Integer shoppingCount =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where user_id = ? and product_id = ? and added_automatically = true",
            Integer.class,
            user.getId(),
            productId);
    assertThat(shoppingCount).isEqualTo(1);
  }

  @Test
  void updateQuantityToZeroWithThresholdAddsToShoppingList() throws Exception {
    String email = "stock-qty-zero-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Boulangerie", "#404040");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Pain", typeId, unitId, true);
    UUID stockItemId =
        insertStockItem(user.getId(), productId, BigDecimal.TEN, new BigDecimal("1"), null);

    mockMvc
        .perform(
            patch("/api/stock-items/" + stockItemId + "/quantity")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", 0))))
        .andExpect(status().isOk());

    Integer shoppingCount =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where user_id = ? and product_id = ?",
            Integer.class,
            user.getId(),
            productId);
    assertThat(shoppingCount).isEqualTo(1);
  }

  @Test
  void updateQuantityAboveThresholdDoesNotAddToShoppingList() throws Exception {
    String email = "stock-qty-no-shopping-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Conserves", "#505050");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Haricots", typeId, unitId, true);
    UUID stockItemId =
        insertStockItem(user.getId(), productId, BigDecimal.TEN, new BigDecimal("2"), null);

    mockMvc
        .perform(
            patch("/api/stock-items/" + stockItemId + "/quantity")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", 8))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));

    Integer shoppingCount =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where user_id = ? and product_id = ?",
            Integer.class,
            user.getId(),
            productId);
    assertThat(shoppingCount).isZero();
  }

  @Test
  void updateQuantityDoesNotDuplicateShoppingListEntry() throws Exception {
    String email = "stock-qty-no-dup-" + UUID.randomUUID() + "@test.fr";
    String token = registerActivateAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    UUID catId = saveCategory(user, "Produits laitiers", "#606060");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(user.getId(), catId, "Beurre", typeId, unitId, true);
    UUID stockItemId =
        insertStockItem(user.getId(), productId, new BigDecimal("1"), new BigDecimal("2"), null);
    jdbcTemplate.update(
        "insert into shopping_list_items (id, user_id, product_id, is_checked, added_automatically, added_at)"
            + " values (?, ?, ?, ?, ?, now())",
        UUID.randomUUID(),
        user.getId(),
        productId,
        false,
        true);

    mockMvc
        .perform(
            patch("/api/stock-items/" + stockItemId + "/quantity")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", 0))))
        .andExpect(status().isOk());

    Integer shoppingCount =
        jdbcTemplate.queryForObject(
            "select count(*) from shopping_list_items where user_id = ? and product_id = ?",
            Integer.class,
            user.getId(),
            productId);
    assertThat(shoppingCount).isEqualTo(1);
  }

  @Test
  void updateQuantityOwnedByAnotherUserReturnsForbidden() throws Exception {
    String ownerEmail = "stock-qty-owner-" + UUID.randomUUID() + "@test.fr";
    registerActivateAndLogin(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
    UUID catId = saveCategory(owner, "Prive3", "#707070");
    UUID typeId = weightTypeId();
    UUID unitId = kgUnitId(typeId);
    UUID productId = insertProduct(owner.getId(), catId, "Jambon", typeId, unitId, true);
    UUID stockItemId = insertStockItem(owner.getId(), productId, BigDecimal.TEN, null, null);

    String outsiderToken =
        registerActivateAndLogin("stock-qty-outsider-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            patch("/api/stock-items/" + stockItemId + "/quantity")
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", 1))))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateQuantityWithUnknownStockItemReturnsNotFound() throws Exception {
    String token = registerActivateAndLogin("stock-qty-404-" + UUID.randomUUID() + "@test.fr");

    mockMvc
        .perform(
            patch("/api/stock-items/" + UUID.randomUUID() + "/quantity")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", 1))))
        .andExpect(status().isNotFound());
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
      UUID userId, UUID catId, String name, UUID typeId, UUID unitId, boolean visible) {
    UUID productId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into products (id, user_id, category_id, name, quantity_type_id, base_unit_id, is_visible)"
            + " values (?, ?, ?, ?, ?, ?, ?)",
        productId,
        userId,
        catId,
        name,
        typeId,
        unitId,
        visible);
    return productId;
  }

  private UUID insertStockItem(
      UUID userId,
      UUID productId,
      BigDecimal quantity,
      BigDecimal lowThreshold,
      LocalDate expirationDate) {
    UUID stockItemId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into stock_items (id, user_id, product_id, quantity, low_threshold, expiration_date, created_at, updated_at)"
            + " values (?, ?, ?, ?, ?, ?, now(), now())",
        stockItemId,
        userId,
        productId,
        quantity,
        lowThreshold,
        expirationDate);
    return stockItemId;
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
