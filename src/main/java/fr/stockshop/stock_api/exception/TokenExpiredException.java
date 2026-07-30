package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class TokenExpiredException extends ApiException {

  public TokenExpiredException() {
    super(HttpStatus.GONE.value(), "error.token.confirmationExpired");
  }
}
