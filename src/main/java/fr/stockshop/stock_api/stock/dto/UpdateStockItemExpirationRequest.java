package fr.stockshop.stock_api.stock.dto;

import jakarta.validation.constraints.FutureOrPresent;
import java.time.LocalDate;

public record UpdateStockItemExpirationRequest(
    @FutureOrPresent(message = "{validation.stockItem.expirationDate.past}")
        LocalDate expirationDate) {}
