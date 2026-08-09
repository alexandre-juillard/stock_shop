package fr.stockshop.stock_api.quantity.repository;

import fr.stockshop.stock_api.quantity.entity.QuantityType;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuantityUnitRepository extends JpaRepository<QuantityUnit, UUID> {

  List<QuantityUnit> findByQuantityTypeOrderBySortOrderAsc(QuantityType quantityType);
}
