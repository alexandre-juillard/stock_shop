package fr.stockshop.stock_api.notification.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.notification.entity.PushPlatform;
import fr.stockshop.stock_api.notification.entity.PushToken;
import fr.stockshop.stock_api.notification.repository.PushTokenRepository;
import fr.stockshop.stock_api.notification.service.PushNotificationSender;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.repository.StockItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class ExpirationNotificationJobTest {

  @Mock private StockItemRepository stockItemRepository;
  @Mock private PushTokenRepository pushTokenRepository;
  @Mock private PushNotificationSender pushNotificationSender;
  @Mock private MessageSource messageSource;

  @InjectMocks private ExpirationNotificationJob job;

  private static User user(UUID id, int expirationAlertDays) {
    return User.builder()
        .id(id)
        .email("user-" + id + "@test.fr")
        .firstName("Alice")
        .lastName("Dupont")
        .expirationAlertDays(expirationAlertDays)
        .preferredLocale("fr")
        .build();
  }

  private static StockItem stockItem(User owner, LocalDate expirationDate) {
    return StockItem.builder()
        .id(UUID.randomUUID())
        .user(owner)
        .expirationDate(expirationDate)
        .build();
  }

  private void stubMessages() {
    lenient()
        .when(
            messageSource.getMessage(eq("notification.expiration.title"), any(), any(Locale.class)))
        .thenReturn("Des produits arrivent à expiration");
    lenient()
        .when(
            messageSource.getMessage(
                eq("notification.expiration.body.single"), any(), any(Locale.class)))
        .thenReturn("Un produit arrive à expiration");
    lenient()
        .when(
            messageSource.getMessage(
                eq("notification.expiration.body.plural"), any(), any(Locale.class)))
        .thenReturn("Plusieurs produits arrivent à expiration");
  }

  @Test
  void doesNothingWhenNoCandidates() {
    when(stockItemRepository.findExpirationNotificationCandidates()).thenReturn(List.of());

    job.notifyExpiringStockItems();

    verify(pushNotificationSender, never()).sendToTokens(anyList(), any(), any());
    verify(stockItemRepository, never()).markExpiryNotified(any(), any());
    verify(pushTokenRepository, never()).deleteByTokenIn(any());
  }

  @Test
  void skipsItemOutsideAlertWindow() {
    User owner = user(UUID.randomUUID(), 3);
    StockItem farItem = stockItem(owner, LocalDate.now().plusDays(10));
    when(stockItemRepository.findExpirationNotificationCandidates()).thenReturn(List.of(farItem));

    job.notifyExpiringStockItems();

    verify(pushNotificationSender, never()).sendToTokens(anyList(), any(), any());
    verify(stockItemRepository, never()).markExpiryNotified(any(), any());
  }

  @Test
  void doesNotSendOrMarkWhenUserHasNoPushToken() {
    User owner = user(UUID.randomUUID(), 3);
    StockItem item = stockItem(owner, LocalDate.now().plusDays(1));
    when(stockItemRepository.findExpirationNotificationCandidates()).thenReturn(List.of(item));
    when(pushTokenRepository.findByUserIdIn(any())).thenReturn(List.of());

    job.notifyExpiringStockItems();

    verify(pushNotificationSender, never()).sendToTokens(anyList(), any(), any());
    verify(stockItemRepository, never()).markExpiryNotified(any(), any());
  }

  @Test
  void sendsSingularMessageAndMarksItemNotifiedWhenOneItemExpiringAndTokenExists() {
    stubMessages();
    User owner = user(UUID.randomUUID(), 3);
    StockItem item = stockItem(owner, LocalDate.now().plusDays(1));
    PushToken token =
        PushToken.builder()
            .id(UUID.randomUUID())
            .user(owner)
            .token("token-1")
            .platform(PushPlatform.ANDROID)
            .build();
    when(stockItemRepository.findExpirationNotificationCandidates()).thenReturn(List.of(item));
    when(pushTokenRepository.findByUserIdIn(any())).thenReturn(List.of(token));
    when(pushNotificationSender.sendToTokens(any(), any(), any())).thenReturn(List.of());

    job.notifyExpiringStockItems();

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(pushNotificationSender)
        .sendToTokens(
            eq(List.of("token-1")), eq("Des produits arrivent à expiration"), bodyCaptor.capture());
    assertThat(bodyCaptor.getValue()).isEqualTo("Un produit arrive à expiration");
    verify(stockItemRepository).markExpiryNotified(eq(List.of(item.getId())), eq(LocalDate.now()));
    verify(pushTokenRepository, never()).deleteByTokenIn(any());
  }

  @Test
  void sendsPluralMessageWhenMultipleItemsExpiringForSameUser() {
    stubMessages();
    User owner = user(UUID.randomUUID(), 5);
    StockItem item1 = stockItem(owner, LocalDate.now().plusDays(1));
    StockItem item2 = stockItem(owner, LocalDate.now().plusDays(2));
    PushToken token =
        PushToken.builder()
            .id(UUID.randomUUID())
            .user(owner)
            .token("token-1")
            .platform(PushPlatform.ANDROID)
            .build();
    when(stockItemRepository.findExpirationNotificationCandidates())
        .thenReturn(List.of(item1, item2));
    when(pushTokenRepository.findByUserIdIn(any())).thenReturn(List.of(token));
    when(pushNotificationSender.sendToTokens(any(), any(), any())).thenReturn(List.of());

    job.notifyExpiringStockItems();

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(pushNotificationSender).sendToTokens(any(), any(), bodyCaptor.capture());
    assertThat(bodyCaptor.getValue()).isEqualTo("Plusieurs produits arrivent à expiration");
    verify(stockItemRepository, times(1)).markExpiryNotified(any(), eq(LocalDate.now()));
  }

  @Test
  void removesInvalidTokensReportedBySender() {
    stubMessages();
    User owner = user(UUID.randomUUID(), 3);
    StockItem item = stockItem(owner, LocalDate.now());
    PushToken token =
        PushToken.builder()
            .id(UUID.randomUUID())
            .user(owner)
            .token("dead-token")
            .platform(PushPlatform.IOS)
            .build();
    when(stockItemRepository.findExpirationNotificationCandidates()).thenReturn(List.of(item));
    when(pushTokenRepository.findByUserIdIn(any())).thenReturn(List.of(token));
    when(pushNotificationSender.sendToTokens(any(), any(), any()))
        .thenReturn(List.of("dead-token"));

    job.notifyExpiringStockItems();

    verify(pushTokenRepository).deleteByTokenIn(eq(java.util.Set.of("dead-token")));
    verify(stockItemRepository).markExpiryNotified(eq(List.of(item.getId())), eq(LocalDate.now()));
  }
}
