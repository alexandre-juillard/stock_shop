package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class TokenNotFoundException extends ApiException {

  public TokenNotFoundException() {
    super(HttpStatus.NOT_FOUND.value(), "error.token.confirmationNotFound");
  }
}
