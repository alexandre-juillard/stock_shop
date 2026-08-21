package fr.stockshop.stock_api.notification.service;

import java.util.List;

/**
 * Abstraction du canal d'envoi de notifications push, permettant de substituer l'implémentation
 * réelle (Firebase Admin SDK) par un double de test.
 */
public interface PushNotificationSender {

  /**
   * Envoie une notification aux tokens fournis. Retourne la liste des tokens détectés comme
   * invalides (ex : appareil désinstallé) afin qu'ils soient supprimés.
   */
  List<String> sendToTokens(List<String> tokens, String title, String body);
}
