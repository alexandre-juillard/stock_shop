package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
    @NotBlank(message = "{validation.refreshToken.required}") String refreshToken) {}
