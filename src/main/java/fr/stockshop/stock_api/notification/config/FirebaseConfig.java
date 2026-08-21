package fr.stockshop.stock_api.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;

/**
 * Initialise le SDK Firebase Admin (FCM) à partir d'un fichier de credentials de compte de service,
 * monté en volume Docker (jamais commité dans le dépôt, voir .env.example). Désactivé en profil
 * "test" (voir {@link fr.stockshop.stock_api.notification.service.NoopPushNotificationSender}).
 */
@Configuration
@Profile("!test")
public class FirebaseConfig {

  @Bean
  public FirebaseApp firebaseApp(
      @Value("${app.push.firebase-credentials-path}") Resource credentialsResource)
      throws IOException {
    if (!FirebaseApp.getApps().isEmpty()) {
      return FirebaseApp.getInstance();
    }
    try (InputStream serviceAccount = credentialsResource.getInputStream()) {
      FirebaseOptions options =
          FirebaseOptions.builder()
              .setCredentials(GoogleCredentials.fromStream(serviceAccount))
              .build();
      return FirebaseApp.initializeApp(options);
    }
  }

  @Bean
  public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
    return FirebaseMessaging.getInstance(firebaseApp);
  }
}
