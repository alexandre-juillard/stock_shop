package fr.stockshop.stock_api.quantity.mapper;

import fr.stockshop.stock_api.quantity.dto.QuantityTypeResponse;
import fr.stockshop.stock_api.quantity.dto.QuantityUnitResponse;
import fr.stockshop.stock_api.quantity.entity.QuantityType;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import org.springframework.stereotype.Component;

@Component
public class QuantityReferenceMapper {

  public QuantityTypeResponse toTypeResponse(QuantityType quantityType) {
    return new QuantityTypeResponse(
        quantityType.getId(), quantityType.getCode(), quantityType.getLabel());
  }

  public QuantityUnitResponse toUnitResponse(QuantityUnit quantityUnit) {
    return new QuantityUnitResponse(
        quantityUnit.getId(),
        quantityUnit.getCode(),
        quantityUnit.getLabel(),
        quantityUnit.getConversionFactor(),
        quantityUnit.isBaseUnit());
  }
}
