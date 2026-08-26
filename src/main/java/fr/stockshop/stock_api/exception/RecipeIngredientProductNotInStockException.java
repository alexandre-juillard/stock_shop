package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class RecipeIngredientProductNotInStockException extends ApiException {

  public RecipeIngredientProductNotInStockException(UUID productId) {
    super(HttpStatus.BAD_REQUEST.value(), "error.recipe.ingredient.productNotInStock", productId);
  }
}
