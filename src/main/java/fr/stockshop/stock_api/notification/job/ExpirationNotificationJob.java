package fr.stockshop.stock_api.notification.job;

import fr.stockshop.stock_api.notification.entity.PushToken;
import fr.stockshop.stock_api.notification.repository.PushTokenRepository;
import fr.stockshop.stock_api.notification.service.PushNotificationSender;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.repository.StockItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job planifié : notifie chaque jour, en une seule notification groupée par utilisateur, les
 * propriétaires d'ingrédients du stock approchant de leur date d'expiration (selon le délai
 * d'alerte propre à chaque utilisateur), via push FCM/APNs sur tous leurs appareils enregistrés.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpirationNotificationJob {

  private final StockItemRepository stockItemRepository;
  private final PushTokenRepository pushTokenRepository;
  private final PushNotificationSender pushNotificationSender;
  private final MessageSource messageSource;

  @Scheduled(cron = "${app.push.expiration-job-cron:0 0 8 * * *}", zone = "Europe/Paris")
  public void run() {
    log.info("Démarrage du job de notification d'expiration");
    notifyExpiringStockItems();
  }

  /** Logique métier du job, exécutable indépendamment de la planification (tests, relance). */
  @Transactional
  public void notifyExpiringStockItems() {
    LocalDate today = LocalDate.now();

    Map<UUID, List<StockItem>> stockItemsByUserId =
        stockItemRepository.findExpirationNotificationCandidates().stream()
            .filter(item -> isWithinAlertWindow(item, today))
            .collect(Collectors.groupingBy(item -> item.getUser().getId()));

    if (stockItemsByUserId.isEmpty()) {
      return;
    }

    Map<UUID, List<String>> tokensByUserId =
        pushTokenRepository.findByUserIdIn(stockItemsByUserId.keySet()).stream()
            .collect(
                Collectors.groupingBy(
                    pushToken -> pushToken.getUser().getId(),
                    Collectors.mapping(PushToken::getToken, Collectors.toList())));

    Set<String> invalidTokens = new HashSet<>();
    List<UUID> notifiedStockItemIds = new ArrayList<>();

    for (Map.Entry<UUID, List<StockItem>> entry : stockItemsByUserId.entrySet()) {
      List<String> tokens = tokensByUserId.get(entry.getKey());
      if (tokens == null || tokens.isEmpty()) {
        continue;
      }

      List<StockItem> expiringItems = entry.getValue();
      User user = expiringItems.get(0).getUser();
      String title = translate("notification.expiration.title", user, null);
      String body = buildBody(user, expiringItems.size());

      invalidTokens.addAll(pushNotificationSender.sendToTokens(tokens, title, body));
      notifiedStockItemIds.addAll(expiringItems.stream().map(StockItem::getId).toList());
    }

    if (!invalidTokens.isEmpty()) {
      pushTokenRepository.deleteByTokenIn(invalidTokens);
    }
    if (!notifiedStockItemIds.isEmpty()) {
      stockItemRepository.markExpiryNotified(notifiedStockItemIds, today);
    }
  }

  private String buildBody(User user, int expiringItemCount) {
    return expiringItemCount == 1
        ? translate("notification.expiration.body.single", user, null)
        : translate("notification.expiration.body.plural", user, new Object[] {expiringItemCount});
  }

  private static boolean isWithinAlertWindow(StockItem stockItem, LocalDate today) {
    LocalDate threshold = today.plusDays(stockItem.getUser().getExpirationAlertDays());
    return !stockItem.getExpirationDate().isAfter(threshold);
  }

  private String translate(String code, User user, Object[] args) {
    return messageSource.getMessage(code, args, resolveLocale(user));
  }

  private static Locale resolveLocale(User user) {
    String code = user.getPreferredLocale();
    return (code == null || code.isBlank()) ? Locale.FRENCH : Locale.forLanguageTag(code);
  }
}
