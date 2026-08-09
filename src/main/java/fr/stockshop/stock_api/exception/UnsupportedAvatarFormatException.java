package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedAvatarFormatException extends ApiException {

  public UnsupportedAvatarFormatException() {
    super(HttpStatus.BAD_REQUEST.value(), "error.avatar.unsupportedFormat");
  }
}
