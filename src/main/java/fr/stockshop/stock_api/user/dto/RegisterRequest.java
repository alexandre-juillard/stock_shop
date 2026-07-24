package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Requête d'inscription publique. Le rôle attribué est toujours {@code USER}. */
public record RegisterRequest(
    @NotBlank(message = "L'email est obligatoire") @Email(message = "L'email doit être valide")
        String email,
    @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        String password,
    @NotBlank(message = "Le prénom est obligatoire") String firstName,
    @NotBlank(message = "Le nom est obligatoire") String lastName) {}
