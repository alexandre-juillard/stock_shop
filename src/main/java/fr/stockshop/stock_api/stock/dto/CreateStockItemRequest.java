package fr.stockshop.stock_api.stock.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateStockItemRequest(
    @NotNull(message = "{validation.stockItem.productId.required}") UUID productId,
    @NotNull(message = "{validation.stockItem.quantity.required}")
        @PositiveOrZero(message = "{validation.stockItem.quantity.min}")
        BigDecimal quantity,
    @PositiveOrZero(message = "{validation.stockItem.lowThreshold.min}") BigDecimal lowThreshold,
    @FutureOrPresent(message = "{validation.stockItem.expirationDate.past}")
        LocalDate expirationDate) {}
