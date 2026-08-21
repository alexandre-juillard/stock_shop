package fr.stockshop.stock_api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.category.entity.Category;
import fr.stockshop.stock_api.category.repository.CategoryRepository;
import fr.stockshop.stock_api.notification.job.ExpirationNotificationJob;
import fr.stockshop.stock_api.notification.service.PushNotificationSender;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Tests d'intégration du job planifié STK-008, exécuté directement (hors attente du cron). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ExpirationNotificationJobIntegrationTest {

  @Autowired private ExpirationNotificationJob job;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private PushNotificationSender pushNotificationSender;

  @Test
  void sendsNotificationAndMarksItemNotifiedWhenWithinAlertWindowAndTokenExists() {
    User user = createUser(3);
    UUID productId = insertProduct(user);
    UUID stockItemId = insertStockItem(user.getId(), productId, LocalDate.now().plusDays(2), null);
    String deviceToken = insertPushToken(user.getId());
    when(pushNotificationSender.sendToTokens(any(), any(), any())).thenReturn(List.of());

    job.notifyExpiringStockItems();

    verify(pushNotificationSender).sendToTokens(eq(List.of(deviceToken)), any(), any());
    LocalDate lastNotified = queryLastExpiryNotifiedAt(stockItemId);
    assertThat(lastNotified).isEqualTo(LocalDate.now());
  }

  @Test
  void doesNotResendWhenAlreadyNotifiedToday() {
    User user = createUser(3);
    UUID productId = insertProduct(user);
    insertStockItem(user.getId(), productId, LocalDate.now().plusDays(1), LocalDate.now());
    insertPushToken(user.getId());

    job.notifyExpiringStockItems();

    verify(pushNotificationSender, never()).sendToTokens(any(), any(), any());
  }

  @Test
  void doesNotNotifyWhenExpirationOutsideAlertWindow() {
    User user = createUser(3);
    UUID productId = insertProduct(user);
    UUID stockItemId = insertStockItem(user.getId(), productId, LocalDate.now().plusDays(10), null);
    insertPushToken(user.getId());

    job.notifyExpiringStockItems();

    verify(pushNotificationSender, never()).sendToTokens(any(), any(), any());
    assertThat(queryLastExpiryNotifiedAt(stockItemId)).isNull();
  }

  @Test
  void removesInvalidPushTokenReportedBySender() {
    User user = createUser(3);
    UUID productId = insertProduct(user);
    insertStockItem(user.getId(), productId, LocalDate.now(), null);
    String deviceToken = insertPushToken(user.getId());
    when(pushNotificationSender.sendToTokens(any(), any(), any())).thenReturn(List.of(deviceToken));

    job.notifyExpiringStockItems();

    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from push_tokens where token = ?", Integer.class, deviceToken);
    assertThat(count).isZero();
  }

  private User createUser(int expirationAlertDays) {
    String email = "expiration-job-" + UUID.randomUUID() + "@test.fr";
    User user =
        User.builder()
            .email(email)
            .firstName("Alice")
            .lastName("Dupont")
            .passwordHash("hash")
            .active(true)
            .expirationAlertDays(expirationAlertDays)
            .preferredLocale("fr")
            .build();
    return userRepository.save(user);
  }

  private UUID insertProduct(User user) {
    UUID categoryId =
        categoryRepository
            .save(
                Category.builder()
                    .user(user)
                    .name("Frigo-" + UUID.randomUUID())
                    .color("#123456")
                    .build())
            .getId();
    UUID typeId =
        jdbcTemplate.queryForObject(
            "select id from quantity_types where code = ?", UUID.class, "weight");
    UUID unitId =
        jdbcTemplate.queryForObject(
            "select id from quantity_units where code = ? and quantity_type_id = ?",
            UUID.class,
            "kg",
            typeId);
    UUID productId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into products (id, user_id, category_id, name, quantity_type_id, base_unit_id, is_visible)"
            + " values (?, ?, ?, ?, ?, ?, true)",
        productId,
        user.getId(),
        categoryId,
        "Produit-" + productId,
        typeId,
        unitId);
    return productId;
  }

  private UUID insertStockItem(
      UUID userId, UUID productId, LocalDate expirationDate, LocalDate lastExpiryNotifiedAt) {
    UUID stockItemId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into stock_items (id, user_id, product_id, quantity, expiration_date, "
            + "last_expiry_notified_at, created_at, updated_at) values (?, ?, ?, 1, ?, ?, now(), now())",
        stockItemId,
        userId,
        productId,
        expirationDate,
        lastExpiryNotifiedAt);
    return stockItemId;
  }

  private String insertPushToken(UUID userId) {
    String token = "device-token-" + UUID.randomUUID();
    jdbcTemplate.update(
        "insert into push_tokens (id, user_id, token, platform, created_at, updated_at)"
            + " values (?, ?, ?, 'ANDROID', now(), now())",
        UUID.randomUUID(),
        userId,
        token);
    return token;
  }

  private LocalDate queryLastExpiryNotifiedAt(UUID stockItemId) {
    return jdbcTemplate.queryForObject(
        "select last_expiry_notified_at from stock_items where id = ?",
        LocalDate.class,
        stockItemId);
  }
}
