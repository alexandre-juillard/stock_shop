package fr.stockshop.stock_api.stock.repository;

import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

  @Query(
      "SELECT s FROM StockItem s "
          + "WHERE s.user = :user AND s.product.visible = true "
          + "ORDER BY s.product.category.name ASC, s.product.name ASC")
  List<StockItem> findVisibleByUserOrderByCategoryAndProductName(@Param("user") User user);
}
