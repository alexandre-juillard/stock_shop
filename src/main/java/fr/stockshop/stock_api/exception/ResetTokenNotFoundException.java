package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class ResetTokenNotFoundException extends ApiException {

  public ResetTokenNotFoundException() {
    super(HttpStatus.NOT_FOUND.value(), "error.token.resetNotFound");
  }
}
