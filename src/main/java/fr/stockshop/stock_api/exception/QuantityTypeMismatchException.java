package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class QuantityTypeMismatchException extends ApiException {

  public QuantityTypeMismatchException(UUID quantityTypeId, UUID baseUnitId) {
    super(
        HttpStatus.BAD_REQUEST.value(),
        "error.product.quantityTypeBaseUnitMismatch",
        quantityTypeId,
        baseUnitId);
  }
}
