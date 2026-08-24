package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ShoppingListItemNotFoundException extends ApiException {

  public ShoppingListItemNotFoundException(UUID shoppingListItemId) {
    super(HttpStatus.NOT_FOUND.value(), "error.shoppingListItem.notFound", shoppingListItemId);
  }
}
