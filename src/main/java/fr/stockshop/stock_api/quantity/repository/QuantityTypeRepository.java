package fr.stockshop.stock_api.quantity.repository;

import fr.stockshop.stock_api.quantity.entity.QuantityType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuantityTypeRepository extends JpaRepository<QuantityType, UUID> {

  List<QuantityType> findAllByOrderByCodeAsc();

  Optional<QuantityType> findByCode(String code);
}
