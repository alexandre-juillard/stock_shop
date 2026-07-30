package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Requête d'inscription publique. Le rôle attribué est toujours {@code USER}. */
public record RegisterRequest(
    @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email,
    @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, message = "{validation.password.size}")
        String password,
    @NotBlank(message = "{validation.firstName.required}") String firstName,
    @NotBlank(message = "{validation.lastName.required}") String lastName) {

  // Ne jamais exposer le mot de passe en clair dans les logs
  @Override
  public String toString() {
    return "RegisterRequest[email="
        + email
        + ", firstName="
        + firstName
        + ", lastName="
        + lastName
        + ", password=***]";
  }
}
