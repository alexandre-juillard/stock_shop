package fr.stockshop.stock_api.user.mapper;

import fr.stockshop.stock_api.user.dto.UserProfileResponse;
import fr.stockshop.stock_api.user.dto.UserResponse;
import fr.stockshop.stock_api.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  public UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getRole(),
        user.getPreferredLocale());
  }

  public UserProfileResponse toProfileResponse(User user) {
    return new UserProfileResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getAvatarUrl(),
        user.getTheme(),
        user.getExpirationAlertDays());
  }
}
