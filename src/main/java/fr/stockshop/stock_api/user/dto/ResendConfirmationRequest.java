package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendConfirmationRequest(
    @NotBlank(message = "L'email est obligatoire") @Email(message = "L'email doit être valide")
        String email) {}
