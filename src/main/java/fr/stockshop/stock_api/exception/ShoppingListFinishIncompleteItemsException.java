package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class ShoppingListFinishIncompleteItemsException extends ApiException {

  public ShoppingListFinishIncompleteItemsException(String incompleteItems) {
    super(
        HttpStatus.BAD_REQUEST.value(),
        "error.shoppingList.finish.incompleteCheckedItems",
        incompleteItems);
  }
}
