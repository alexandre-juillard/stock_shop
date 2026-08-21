package fr.stockshop.stock_api.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * NB : {@code SendResponse} et {@code FirebaseMessagingException} sont des classes finales du SDK
 * Firebase Admin, sans constructeur public, que Mockito ne parvient pas à stuber de façon fiable
 * (leurs méthodes réelles s'exécutent malgré le mock). La logique de classification "token
 * invalide" est donc extraite en méthode pure ({@link
 * FirebasePushNotificationSender#isInvalidTokenError}) et testée indépendamment ; ce test se
 * concentre sur le découpage en lots et la résilience aux erreurs FCM, seuls aspects testables sans
 * instancier ces types SDK.
 */
@ExtendWith(MockitoExtension.class)
class FirebasePushNotificationSenderTest {

  @Mock private FirebaseMessaging firebaseMessaging;

  @InjectMocks private FirebasePushNotificationSender sender;

  @Test
  void isInvalidTokenErrorReturnsTrueForUnregisteredAndInvalidArgument() {
    assertThat(FirebasePushNotificationSender.isInvalidTokenError(MessagingErrorCode.UNREGISTERED))
        .isTrue();
    assertThat(
            FirebasePushNotificationSender.isInvalidTokenError(MessagingErrorCode.INVALID_ARGUMENT))
        .isTrue();
  }

  @Test
  void isInvalidTokenErrorReturnsFalseForOtherErrorCodesOrNull() {
    assertThat(FirebasePushNotificationSender.isInvalidTokenError(MessagingErrorCode.UNAVAILABLE))
        .isFalse();
    assertThat(
            FirebasePushNotificationSender.isInvalidTokenError(MessagingErrorCode.QUOTA_EXCEEDED))
        .isFalse();
    assertThat(FirebasePushNotificationSender.isInvalidTokenError(null)).isFalse();
  }

  @Test
  void doesNotPropagateWhenFirebaseThrows() throws FirebaseMessagingException {
    when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
        .thenThrow(mock(FirebaseMessagingException.class));

    List<String> invalidTokens = sender.sendToTokens(List.of("token-1"), "Titre", "Corps");

    assertThat(invalidTokens).isEmpty();
  }

  @Test
  void splitsTokensIntoBatchesOfAtMost500() throws FirebaseMessagingException {
    List<String> tokens =
        IntStream.range(0, 750).mapToObj(i -> "token-" + i).collect(Collectors.toList());
    BatchResponse batchResponse = mock(BatchResponse.class);
    when(batchResponse.getResponses()).thenReturn(List.of());
    when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
        .thenReturn(batchResponse);

    sender.sendToTokens(tokens, "Titre", "Corps");

    verify(firebaseMessaging, times(2)).sendEachForMulticast(any(MulticastMessage.class));
  }
}
