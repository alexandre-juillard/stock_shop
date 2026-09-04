package fr.stockshop.stock_api.recipe.dto;

import java.util.List;

public record RecipeConsumeResponse(List<RecipeConsumeResultItemResponse> results) {

  public RecipeConsumeResponse {
    results = results == null ? List.of() : List.copyOf(results);
  }

  @Override
  public List<RecipeConsumeResultItemResponse> results() {
    return List.copyOf(results);
  }
}
