package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

  public UserNotFoundException(String email) {
    super(HttpStatus.NOT_FOUND.value(), "error.user.notFound", email);
  }
}
