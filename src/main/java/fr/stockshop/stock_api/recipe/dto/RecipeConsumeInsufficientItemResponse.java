package fr.stockshop.stock_api.recipe.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeConsumeInsufficientItemResponse(
    UUID productId, String name, BigDecimal required, BigDecimal available) {}
