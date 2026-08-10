package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class CategoryNameAlreadyExistsException extends ApiException {

  public CategoryNameAlreadyExistsException(String name) {
    super(HttpStatus.CONFLICT.value(), "error.category.name.alreadyExists", name);
  }
}
