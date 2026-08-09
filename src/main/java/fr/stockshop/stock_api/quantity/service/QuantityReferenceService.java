package fr.stockshop.stock_api.quantity.service;

import fr.stockshop.stock_api.exception.QuantityTypeNotFoundException;
import fr.stockshop.stock_api.quantity.dto.QuantityTypeResponse;
import fr.stockshop.stock_api.quantity.dto.QuantityUnitResponse;
import fr.stockshop.stock_api.quantity.entity.QuantityType;
import fr.stockshop.stock_api.quantity.mapper.QuantityReferenceMapper;
import fr.stockshop.stock_api.quantity.repository.QuantityTypeRepository;
import fr.stockshop.stock_api.quantity.repository.QuantityUnitRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuantityReferenceService {

  private final QuantityTypeRepository quantityTypeRepository;
  private final QuantityUnitRepository quantityUnitRepository;
  private final QuantityReferenceMapper quantityReferenceMapper;

  @Transactional(readOnly = true)
  @Cacheable("quantityTypes")
  public List<QuantityTypeResponse> listQuantityTypes() {
    return quantityTypeRepository.findAllByOrderByCodeAsc().stream()
        .map(quantityReferenceMapper::toTypeResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "quantityUnitsByType", key = "#typeCode")
  public List<QuantityUnitResponse> listUnitsByType(String typeCode) {
    QuantityType quantityType =
        quantityTypeRepository
            .findByCode(typeCode)
            .orElseThrow(() -> new QuantityTypeNotFoundException(typeCode));

    return quantityUnitRepository.findByQuantityTypeOrderBySortOrderAsc(quantityType).stream()
        .map(quantityReferenceMapper::toUnitResponse)
        .toList();
  }
}
