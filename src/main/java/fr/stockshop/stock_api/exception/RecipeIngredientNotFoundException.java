package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class RecipeIngredientNotFoundException extends ApiException {

  public RecipeIngredientNotFoundException(UUID productId) {
    super(HttpStatus.NOT_FOUND.value(), "error.recipe.ingredient.notFound", productId);
  }
}
