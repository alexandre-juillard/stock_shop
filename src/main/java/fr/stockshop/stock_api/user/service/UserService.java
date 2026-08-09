package fr.stockshop.stock_api.user.service;

import fr.stockshop.stock_api.common.storage.AvatarStorageService;
import fr.stockshop.stock_api.exception.EmailAlreadyExistsException;
import fr.stockshop.stock_api.exception.UnsupportedLocaleException;
import fr.stockshop.stock_api.user.dto.AvatarResponse;
import fr.stockshop.stock_api.user.dto.UpdateLocaleRequest;
import fr.stockshop.stock_api.user.dto.UpdateProfileRequest;
import fr.stockshop.stock_api.user.dto.UserProfileResponse;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.mapper.UserMapper;
import fr.stockshop.stock_api.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Gère les préférences et le profil du compte connecté. */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final AvatarStorageService avatarStorageService;

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

  @Transactional(readOnly = true)
  public UserProfileResponse getProfile(User currentUser) {
    return userMapper.toProfileResponse(currentUser);
  }

  @Transactional
  public UserProfileResponse updateProfile(User currentUser, UpdateProfileRequest request) {
    if (request.email() != null && !request.email().equalsIgnoreCase(currentUser.getEmail())) {
      if (userRepository.existsByEmail(request.email())) {
        throw new EmailAlreadyExistsException(request.email());
      }
      currentUser.setEmail(request.email());
    }
    if (request.firstName() != null) {
      currentUser.setFirstName(request.firstName());
    }
    if (request.lastName() != null) {
      currentUser.setLastName(request.lastName());
    }

    userRepository.save(currentUser);
    return userMapper.toProfileResponse(currentUser);
  }

  @Transactional
  public AvatarResponse uploadAvatar(User currentUser, MultipartFile fichier) {
    String avatarUrl = avatarStorageService.saveAvatar(currentUser.getId(), fichier);
    currentUser.setAvatarUrl(avatarUrl);
    userRepository.save(currentUser);
    return new AvatarResponse(avatarUrl);
  }

  @Transactional
  public void deleteAvatar(User currentUser) {
    avatarStorageService.deleteAvatar(currentUser.getAvatarUrl());
    currentUser.setAvatarUrl(null);
    userRepository.save(currentUser);
  }
}
