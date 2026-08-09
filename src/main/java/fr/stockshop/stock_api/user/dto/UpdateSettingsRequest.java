package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record UpdateSettingsRequest(
    @Pattern(regexp = "light|dark", message = "{validation.settings.theme.invalid}") String theme,
    @Min(value = 1, message = "{validation.settings.expirationAlertDays.min}")
        Integer expirationAlertDays) {}
