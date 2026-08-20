package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class StockItemAlreadyExistsException extends ApiException {

  public StockItemAlreadyExistsException(String productName) {
    super(HttpStatus.CONFLICT.value(), "error.stockItem.alreadyExists", productName);
  }
}
