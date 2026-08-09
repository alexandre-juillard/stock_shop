package fr.stockshop.stock_api.exception;

import org.springframework.http.HttpStatus;

public class QuantityTypeNotFoundException extends ApiException {

  public QuantityTypeNotFoundException(String typeCode) {
    super(HttpStatus.NOT_FOUND.value(), "error.quantityType.notFound", typeCode);
  }
}
