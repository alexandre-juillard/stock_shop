package fr.stockshop.stock_api.recipe.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateRecipeIngredientRequest(
    @NotNull(message = "{validation.recipe.ingredient.productId.required}") UUID productId,
    @NotNull(message = "{validation.recipe.ingredient.quantity.required}")
        @Positive(message = "{validation.recipe.ingredient.quantity.positive}")
        BigDecimal quantity,
    @NotNull(message = "{validation.recipe.ingredient.unitId.required}") UUID unitId) {}
