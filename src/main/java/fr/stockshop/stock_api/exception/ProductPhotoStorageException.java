package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class ProductPhotoStorageException extends ApiException {

  public ProductPhotoStorageException(Throwable cause) {
    super(HttpStatus.INTERNAL_SERVER_ERROR.value(), "error.product.photo.storageFailure");
    initCause(cause);
  }
}
