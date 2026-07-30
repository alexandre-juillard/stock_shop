package fr.stockshop.stock_api.user.service;

import fr.stockshop.stock_api.exception.UnsupportedLocaleException;
import fr.stockshop.stock_api.user.dto.UpdateLocaleRequest;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gère les préférences du profil utilisateur (langue d'affichage utilisée pour les emails et les
 * messages traduits de l'API).
 */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  @Value("#{'${app.i18n.supported-locales:fr,en}'.split(',')}")
  private List<String> supportedLocales;

  @Transactional
  public void updatePreferredLocale(User currentUser, UpdateLocaleRequest request) {
    String locale = request.locale().toLowerCase();
    if (!supportedLocales.contains(locale)) {
      throw new UnsupportedLocaleException(locale, supportedLocales);
    }

    currentUser.setPreferredLocale(locale);
    userRepository.save(currentUser);
  }
}
