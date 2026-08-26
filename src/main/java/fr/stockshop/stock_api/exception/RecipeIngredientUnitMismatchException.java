package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class RecipeIngredientUnitMismatchException extends ApiException {

  public RecipeIngredientUnitMismatchException(UUID productId, UUID unitId) {
    super(
        HttpStatus.BAD_REQUEST.value(), "error.recipe.ingredient.unitMismatch", productId, unitId);
  }
}
