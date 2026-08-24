package fr.stockshop.stock_api.shoppinglist.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FinishShoppingListItemResultResponse(
    UUID shoppingListItemId,
    UUID productId,
    String productName,
    UUID stockItemId,
    String action,
    BigDecimal addedQuantity,
    BigDecimal resultingStockQuantity) {}
