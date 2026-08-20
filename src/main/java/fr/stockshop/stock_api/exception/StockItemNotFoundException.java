package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class StockItemNotFoundException extends ApiException {

  public StockItemNotFoundException(UUID stockItemId) {
    super(HttpStatus.NOT_FOUND.value(), "error.stockItem.notFound", stockItemId);
  }
}
