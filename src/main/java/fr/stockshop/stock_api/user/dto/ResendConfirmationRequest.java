package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendConfirmationRequest(
    @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email) {}
