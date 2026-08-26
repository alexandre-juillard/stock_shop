package fr.stockshop.stock_api.recipe.dto;

import java.math.BigDecimal;

public record RecipeIngredientResponse(
    RecipeProductReferenceResponse product,
    BigDecimal quantity,
    RecipeUnitReferenceResponse unit) {}
