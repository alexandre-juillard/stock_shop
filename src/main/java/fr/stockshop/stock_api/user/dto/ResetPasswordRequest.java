package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = "{validation.token.required}") String token,
    @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, message = "{validation.password.size}")
        String newPassword) {

  // Ne jamais exposer le mot de passe en clair dans les logs
  @Override
  public String toString() {
    return "ResetPasswordRequest[token=***, newPassword=***]";
  }
}
