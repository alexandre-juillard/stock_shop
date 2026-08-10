package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class QuantityUnitNotFoundException extends ApiException {

  public QuantityUnitNotFoundException(UUID unitId) {
    super(HttpStatus.NOT_FOUND.value(), "error.quantityUnit.notFound", unitId);
  }
}
