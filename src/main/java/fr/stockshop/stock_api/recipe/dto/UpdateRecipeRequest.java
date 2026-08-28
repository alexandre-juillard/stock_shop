package fr.stockshop.stock_api.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRecipeRequest(
    @NotBlank(message = "{validation.recipe.name.required}")
        @Size(max = 200, message = "{validation.recipe.name.max}")
        String name) {}
