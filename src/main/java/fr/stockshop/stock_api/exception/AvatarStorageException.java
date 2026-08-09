package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class AvatarStorageException extends ApiException {

  public AvatarStorageException(Throwable cause) {
    super(HttpStatus.INTERNAL_SERVER_ERROR.value(), "error.avatar.storageFailure");
    initCause(cause);
  }
}
