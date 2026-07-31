package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class ResetTokenExpiredException extends ApiException {

  public ResetTokenExpiredException() {
    super(HttpStatus.GONE.value(), "error.token.resetExpired");
  }
}
