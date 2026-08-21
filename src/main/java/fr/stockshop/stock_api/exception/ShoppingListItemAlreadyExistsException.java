package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class ShoppingListItemAlreadyExistsException extends ApiException {

  public ShoppingListItemAlreadyExistsException(String productName) {
    super(HttpStatus.CONFLICT.value(), "error.shoppingListItem.alreadyExists", productName);
  }
}
