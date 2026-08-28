package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class RecipeIngredientAlreadyExistsException extends ApiException {

  public RecipeIngredientAlreadyExistsException(UUID productId) {
    super(HttpStatus.CONFLICT.value(), "error.recipe.ingredient.alreadyExists", productId);
  }
}
