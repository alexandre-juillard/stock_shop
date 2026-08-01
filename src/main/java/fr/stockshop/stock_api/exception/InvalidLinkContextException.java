package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class InvalidLinkContextException extends ApiException {

  public InvalidLinkContextException() {
    super(HttpStatus.BAD_REQUEST.value(), "error.oauth2.linkContextInvalid");
  }
}
