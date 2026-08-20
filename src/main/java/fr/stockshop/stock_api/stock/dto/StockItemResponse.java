package fr.stockshop.stock_api.stock.dto;

import fr.stockshop.stock_api.stock.entity.StockItemStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockItemResponse(
    UUID id,
    StockItemProductSummary product,
    BigDecimal quantity,
    BigDecimal lowThreshold,
    LocalDate expirationDate,
    StockItemStatus status,
    boolean needsQuantityUpdate) {}
