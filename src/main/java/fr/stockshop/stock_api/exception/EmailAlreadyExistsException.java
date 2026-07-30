package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends ApiException {

  public EmailAlreadyExistsException(String email) {
    super(HttpStatus.CONFLICT.value(), "error.email.alreadyExists", email);
  }
}
