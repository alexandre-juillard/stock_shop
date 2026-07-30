package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailRequest(@NotBlank(message = "Le token est obligatoire") String token) {}
