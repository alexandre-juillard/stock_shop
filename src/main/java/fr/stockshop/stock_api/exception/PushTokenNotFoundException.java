package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class PushTokenNotFoundException extends ApiException {

  public PushTokenNotFoundException(String token) {
    super(HttpStatus.NOT_FOUND.value(), "error.pushToken.notFound", token);
  }
}
