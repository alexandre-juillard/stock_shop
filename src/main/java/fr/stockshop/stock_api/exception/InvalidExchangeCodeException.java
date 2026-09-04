package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class InvalidExchangeCodeException extends ApiException {

  public InvalidExchangeCodeException() {
    super(HttpStatus.BAD_REQUEST.value(), "error.oauth2.exchangeCodeInvalid");
  }
}
