package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class AccountAlreadyActiveException extends ApiException {

  public AccountAlreadyActiveException(String email) {
    super(HttpStatus.CONFLICT.value(), "error.account.alreadyActive", email);
  }
}
