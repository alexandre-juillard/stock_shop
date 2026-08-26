package fr.stockshop.stock_api.recipe.dto;

import java.util.UUID;

public record RecipeUnitReferenceResponse(UUID id, String code, String label) {}
