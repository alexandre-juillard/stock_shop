package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class RecipeNotFoundException extends ApiException {

  public RecipeNotFoundException(UUID recipeId) {
    super(HttpStatus.NOT_FOUND.value(), "error.recipe.notFound", recipeId);
  }
}
