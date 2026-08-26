package fr.stockshop.stock_api.recipe.dto;

import java.time.Instant;
import java.util.UUID;

public record RecipeSummaryResponse(
    UUID id, String name, long ingredientCount, Instant createdAt) {}
