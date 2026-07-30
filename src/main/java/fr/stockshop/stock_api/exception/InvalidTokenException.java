package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends ApiException {

  public InvalidTokenException(String messageCode) {
    super(HttpStatus.UNAUTHORIZED.value(), messageCode);
  }
}
