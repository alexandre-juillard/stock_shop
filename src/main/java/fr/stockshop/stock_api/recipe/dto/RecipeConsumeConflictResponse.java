package fr.stockshop.stock_api.recipe.dto;

import java.util.List;

public record RecipeConsumeConflictResponse(
    List<RecipeConsumeMissingItemResponse> missing,
    List<RecipeConsumeInsufficientItemResponse> insufficient) {

  public RecipeConsumeConflictResponse {
    missing = missing == null ? List.of() : List.copyOf(missing);
    insufficient = insufficient == null ? List.of() : List.copyOf(insufficient);
  }

  @Override
  public List<RecipeConsumeMissingItemResponse> missing() {
    return List.copyOf(missing);
  }

  @Override
  public List<RecipeConsumeInsufficientItemResponse> insufficient() {
    return List.copyOf(insufficient);
  }
}
