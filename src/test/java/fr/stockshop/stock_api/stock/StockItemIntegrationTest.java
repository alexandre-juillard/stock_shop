package fr.stockshop.stock_api.stock;

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

  private void insertStockItem(
      UUID userId,
      UUID productId,
      BigDecimal quantity,
      BigDecimal lowThreshold,
      LocalDate expirationDate) {
    jdbcTemplate.update(
        "insert into stock_items (id, user_id, product_id, quantity, low_threshold, expiration_date, created_at, updated_at)"
            + " values (?, ?, ?, ?, ?, ?, now(), now())",
        UUID.randomUUID(),
        userId,
        productId,
        quantity,
        lowThreshold,
        expirationDate);
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
