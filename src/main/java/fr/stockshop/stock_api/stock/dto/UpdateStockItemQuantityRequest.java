package fr.stockshop.stock_api.stock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record UpdateStockItemQuantityRequest(
    @NotNull(message = "{validation.stockItem.quantity.required}")
        @PositiveOrZero(message = "{validation.stockItem.quantity.min}")
        BigDecimal quantity) {}
