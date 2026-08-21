package fr.stockshop.stock_api.notification.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Envoie les notifications push via Firebase Cloud Messaging (couvre Android et iOS/APNs en un seul
 * appel), en respectant la limite FCM de 500 tokens par requête multicast.
 */
@Service
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class FirebasePushNotificationSender implements PushNotificationSender {

  private static final int MAX_TOKENS_PER_BATCH = 500;

  private final FirebaseMessaging firebaseMessaging;

  @Override
  public List<String> sendToTokens(List<String> tokens, String title, String body) {
    List<String> invalidTokens = new ArrayList<>();
    Notification notification = Notification.builder().setTitle(title).setBody(body).build();

    for (int start = 0; start < tokens.size(); start += MAX_TOKENS_PER_BATCH) {
      List<String> batch =
          tokens.subList(start, Math.min(start + MAX_TOKENS_PER_BATCH, tokens.size()));
      invalidTokens.addAll(sendBatch(batch, notification));
    }
    return invalidTokens;
  }

  private List<String> sendBatch(List<String> batch, Notification notification) {
    List<String> invalidTokens = new ArrayList<>();
    MulticastMessage message =
        MulticastMessage.builder().setNotification(notification).addAllTokens(batch).build();
    try {
      BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
      List<SendResponse> responses = response.getResponses();
      for (int i = 0; i < responses.size(); i++) {
        SendResponse sendResponse = responses.get(i);
        if (sendResponse.isSuccessful()) {
          continue;
        }
        MessagingErrorCode errorCode =
            sendResponse.getException() != null
                ? sendResponse.getException().getMessagingErrorCode()
                : null;
        if (isInvalidTokenError(errorCode)) {
          invalidTokens.add(batch.get(i));
        } else {
          log.warn("Échec d'envoi push non lié à un token invalide : {}", errorCode);
        }
      }
    } catch (FirebaseMessagingException ex) {
      log.error("Échec de l'envoi push en batch ({} destinataires)", batch.size(), ex);
    }
    return invalidTokens;
  }

  /**
   * Détermine si un code d'erreur FCM signifie que le token doit être supprimé : appareil
   * désinstallé ou token structurellement invalide. Extrait en méthode pure pour être testable
   * indépendamment des types SDK Firebase (classes finales non simulables).
   */
  static boolean isInvalidTokenError(MessagingErrorCode errorCode) {
    return errorCode == MessagingErrorCode.UNREGISTERED
        || errorCode == MessagingErrorCode.INVALID_ARGUMENT;
  }
}
