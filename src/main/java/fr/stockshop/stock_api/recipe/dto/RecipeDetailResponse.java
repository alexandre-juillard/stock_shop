package fr.stockshop.stock_api.recipe.dto;

import java.util.List;
import java.util.UUID;

public record RecipeDetailResponse(
    UUID id, String name, List<RecipeIngredientResponse> ingredients) {

  public RecipeDetailResponse {
    ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
  }

  @Override
  public List<RecipeIngredientResponse> ingredients() {
    return List.copyOf(ingredients);
  }
}
