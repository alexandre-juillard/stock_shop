package fr.stockshop.stock_api.recipe.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeConsumeResultItemResponse(
    UUID productId,
    String name,
    BigDecimal deducted,
    BigDecimal newStockQuantity,
    boolean forced) {}
