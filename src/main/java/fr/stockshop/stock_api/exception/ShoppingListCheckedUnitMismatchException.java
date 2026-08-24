package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ShoppingListCheckedUnitMismatchException extends ApiException {

  public ShoppingListCheckedUnitMismatchException(UUID shoppingListItemId, UUID checkedUnitId) {
    super(
        HttpStatus.BAD_REQUEST.value(),
        "error.shoppingListItem.checkedUnitMismatch",
        shoppingListItemId,
        checkedUnitId);
  }
}
