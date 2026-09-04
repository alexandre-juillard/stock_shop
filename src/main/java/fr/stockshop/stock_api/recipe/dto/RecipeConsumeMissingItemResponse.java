package fr.stockshop.stock_api.recipe.dto;

import java.util.UUID;

public record RecipeConsumeMissingItemResponse(UUID productId, String name) {}
