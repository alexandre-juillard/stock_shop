package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedProductPhotoFormatException extends ApiException {

  public UnsupportedProductPhotoFormatException() {
    super(HttpStatus.BAD_REQUEST.value(), "error.product.photo.unsupportedFormat");
  }
}
