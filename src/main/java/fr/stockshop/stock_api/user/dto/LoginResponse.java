package fr.stockshop.stock_api.user.dto;

import java.util.UUID;

/** Réponse renvoyée après une connexion réussie (POST /api/auth/login). */
public record LoginResponse(String accessToken, String refreshToken, UserSummary user) {

  /** Sous-ensemble des informations du compte utile immédiatement après connexion. */
  public record UserSummary(
      UUID id,
      String email,
      String firstName,
      String lastName,
      String theme,
      int expirationAlertDays) {}
}
