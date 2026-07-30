package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class TokenExpiredException extends ApiException {

  public TokenExpiredException(String message) {
    super(HttpStatus.GONE.value(), message);
  }
}
