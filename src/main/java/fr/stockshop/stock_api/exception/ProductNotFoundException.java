package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ProductNotFoundException extends ApiException {

  public ProductNotFoundException(UUID productId) {
    super(HttpStatus.NOT_FOUND.value(), "error.product.notFound", productId);
  }
}
