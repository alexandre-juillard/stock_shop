package fr.stockshop.stock_api.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.exception.PushTokenNotFoundException;
import fr.stockshop.stock_api.notification.dto.RegisterPushTokenRequest;
import fr.stockshop.stock_api.notification.entity.PushPlatform;
import fr.stockshop.stock_api.notification.entity.PushToken;
import fr.stockshop.stock_api.notification.repository.PushTokenRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

  @Mock private PushTokenRepository pushTokenRepository;

  @InjectMocks private PushTokenService pushTokenService;

  private static User user(UUID id) {
    return User.builder()
        .id(id)
        .email("user-" + id + "@test.fr")
        .firstName("Alice")
        .lastName("Dupont")
        .build();
  }

  @Test
  void registerTokenCreatesNewEntryWhenTokenUnknown() {
    User currentUser = user(UUID.randomUUID());
    RegisterPushTokenRequest request =
        new RegisterPushTokenRequest("token-abc", PushPlatform.ANDROID);
    when(pushTokenRepository.findByToken("token-abc")).thenReturn(Optional.empty());

    boolean created = pushTokenService.registerToken(currentUser, request);

    ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
    verify(pushTokenRepository).save(captor.capture());
    assertThat(created).isTrue();
    assertThat(captor.getValue().getToken()).isEqualTo("token-abc");
    assertThat(captor.getValue().getPlatform()).isEqualTo(PushPlatform.ANDROID);
    assertThat(captor.getValue().getUser()).isEqualTo(currentUser);
  }

  @Test
  void registerTokenReassignsOwnerWhenTokenAlreadyExistsForAnotherUser() {
    User previousOwner = user(UUID.randomUUID());
    User currentUser = user(UUID.randomUUID());
    PushToken existing =
        PushToken.builder()
            .id(UUID.randomUUID())
            .token("token-abc")
            .user(previousOwner)
            .platform(PushPlatform.IOS)
            .build();
    when(pushTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(existing));

    boolean created =
        pushTokenService.registerToken(
            currentUser, new RegisterPushTokenRequest("token-abc", PushPlatform.ANDROID));

    ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
    verify(pushTokenRepository).save(captor.capture());
    assertThat(created).isFalse();
    assertThat(captor.getValue().getUser()).isEqualTo(currentUser);
    assertThat(captor.getValue().getPlatform()).isEqualTo(PushPlatform.ANDROID);
  }

  @Test
  void registerTokenReturnsFalseWhenAlreadyOwnedByCurrentUser() {
    User currentUser = user(UUID.randomUUID());
    PushToken existing =
        PushToken.builder()
            .id(UUID.randomUUID())
            .token("token-abc")
            .user(currentUser)
            .platform(PushPlatform.ANDROID)
            .build();
    when(pushTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(existing));

    boolean created =
        pushTokenService.registerToken(
            currentUser, new RegisterPushTokenRequest("token-abc", PushPlatform.ANDROID));

    assertThat(created).isFalse();
    verify(pushTokenRepository).save(existing);
  }

  @Test
  void unregisterTokenDeletesWhenOwnedByCurrentUser() {
    User currentUser = user(UUID.randomUUID());
    PushToken existing =
        PushToken.builder()
            .id(UUID.randomUUID())
            .token("token-abc")
            .user(currentUser)
            .platform(PushPlatform.ANDROID)
            .build();
    when(pushTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(existing));

    pushTokenService.unregisterToken(currentUser, "token-abc");

    verify(pushTokenRepository).delete(existing);
  }

  @Test
  void unregisterTokenThrowsNotFoundWhenTokenDoesNotExist() {
    User currentUser = user(UUID.randomUUID());
    when(pushTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> pushTokenService.unregisterToken(currentUser, "unknown"))
        .isInstanceOf(PushTokenNotFoundException.class);
    verify(pushTokenRepository, never()).delete(any());
  }

  @Test
  void unregisterTokenThrowsForbiddenWhenTokenOwnedByAnotherUser() {
    User owner = user(UUID.randomUUID());
    User currentUser = user(UUID.randomUUID());
    PushToken existing =
        PushToken.builder()
            .id(UUID.randomUUID())
            .token("token-abc")
            .user(owner)
            .platform(PushPlatform.ANDROID)
            .build();
    when(pushTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> pushTokenService.unregisterToken(currentUser, "token-abc"))
        .isInstanceOf(AccessDeniedException.class);
    verify(pushTokenRepository, never()).delete(any());
  }
}
