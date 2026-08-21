package fr.stockshop.stock_api.notification.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Double de {@link PushNotificationSender} actif en profil "test" : évite de nécessiter de vrais
 * credentials Firebase pour démarrer le contexte Spring dans les tests. Les tests dédiés au job de
 * notification remplacent ce bean par un mock via {@code @MockitoBean}.
 */
@Service
@Profile("test")
@Slf4j
public class NoopPushNotificationSender implements PushNotificationSender {

  @Override
  public List<String> sendToTokens(List<String> tokens, String title, String body) {
    log.debug("Envoi push simulé (profil test) : {} destinataire(s)", tokens.size());
    return List.of();
  }
}
