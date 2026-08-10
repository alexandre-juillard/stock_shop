package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class ProductNameAlreadyExistsException extends ApiException {

  public ProductNameAlreadyExistsException(String name) {
    super(HttpStatus.CONFLICT.value(), "error.product.name.alreadyExists", name);
  }
}
