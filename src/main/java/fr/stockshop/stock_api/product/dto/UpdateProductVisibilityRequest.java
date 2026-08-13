package fr.stockshop.stock_api.product.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateProductVisibilityRequest(
    @NotNull(message = "isVisible is required") Boolean isVisible) {}
