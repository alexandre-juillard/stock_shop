package fr.stockshop.stock_api.shoppinglist.repository;

import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.shoppinglist.entity.ShoppingListItem;
import fr.stockshop.stock_api.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {

  boolean existsByUserAndProduct(User user, Product product);

  @Query(
      "SELECT s FROM ShoppingListItem s "
          + "JOIN FETCH s.product p "
          + "JOIN FETCH p.category c "
          + "LEFT JOIN FETCH s.checkedUnit "
          + "WHERE s.user = :user AND p.visible = true "
          + "ORDER BY c.name ASC, p.name ASC")
  List<ShoppingListItem> findVisibleByUserOrderByCategoryAndProductName(@Param("user") User user);
}
