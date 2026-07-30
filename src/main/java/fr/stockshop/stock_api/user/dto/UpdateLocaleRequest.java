package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateLocaleRequest(
    @NotBlank(message = "{validation.locale.required}") String locale) {}
