package fr.stockshop.stock_api.notification.dto;

import fr.stockshop.stock_api.notification.entity.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterPushTokenRequest(
    @NotBlank(message = "{validation.pushToken.token.required}") String token,
    @NotNull(message = "{validation.pushToken.platform.required}") PushPlatform platform) {}
