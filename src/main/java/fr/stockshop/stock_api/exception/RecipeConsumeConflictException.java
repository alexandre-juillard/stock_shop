package fr.stockshop.stock_api.exception;

import fr.stockshop.stock_api.recipe.dto.RecipeConsumeConflictResponse;

public class RecipeConsumeConflictException extends RuntimeException {

  private final RecipeConsumeConflictResponse conflict;

  public RecipeConsumeConflictException(RecipeConsumeConflictResponse conflict) {
    super("Recipe consumption conflict");
    this.conflict = conflict;
  }

  public RecipeConsumeConflictResponse getConflict() {
    return conflict;
  }
}
