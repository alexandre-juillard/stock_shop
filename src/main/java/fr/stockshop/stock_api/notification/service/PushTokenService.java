package fr.stockshop.stock_api.notification.service;

import fr.stockshop.stock_api.exception.PushTokenNotFoundException;
import fr.stockshop.stock_api.notification.dto.RegisterPushTokenRequest;
import fr.stockshop.stock_api.notification.entity.PushToken;
import fr.stockshop.stock_api.notification.repository.PushTokenRepository;
import fr.stockshop.stock_api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushTokenService {

  private final PushTokenRepository pushTokenRepository;

  /**
   * Enregistre le token push de l'appareil courant. Si ce token existe déjà (même appareil,
   * reconnexion avec un autre compte), il est réassigné à l'utilisateur courant plutôt que
   * dupliqué.
   */
  @Transactional
  public void registerToken(User currentUser, RegisterPushTokenRequest request) {
    PushToken pushToken =
        pushTokenRepository
            .findByToken(request.token())
            .orElseGet(() -> PushToken.builder().token(request.token()).build());
    pushToken.setUser(currentUser);
    pushToken.setPlatform(request.platform());
    pushTokenRepository.save(pushToken);
  }

  @Transactional
  public void unregisterToken(User currentUser, String token) {
    PushToken pushToken =
        pushTokenRepository
            .findByToken(token)
            .orElseThrow(() -> new PushTokenNotFoundException(token));
    assertOwnership(pushToken, currentUser);
    pushTokenRepository.delete(pushToken);
  }

  private void assertOwnership(PushToken pushToken, User currentUser) {
    if (!pushToken.getUser().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("Push token does not belong to current user");
    }
  }
}
