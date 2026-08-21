package fr.stockshop.stock_api.stock.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record UpdateStockItemThresholdRequest(
    @PositiveOrZero(message = "{validation.stockItem.lowThreshold.min}") BigDecimal lowThreshold) {}
