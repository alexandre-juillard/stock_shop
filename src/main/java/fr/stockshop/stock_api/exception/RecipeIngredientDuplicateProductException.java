package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class RecipeIngredientDuplicateProductException extends ApiException {

  public RecipeIngredientDuplicateProductException(UUID productId) {
    super(HttpStatus.BAD_REQUEST.value(), "error.recipe.ingredient.duplicateProduct", productId);
  }
}
