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

  long deleteAllByUser(User user);

  @Query(
      "SELECT s FROM ShoppingListItem s "
          + "JOIN FETCH s.product p "
          + "JOIN FETCH p.category c "
          + "LEFT JOIN FETCH s.checkedUnit "
          + "WHERE s.user = :user AND p.visible = true "
          + "ORDER BY c.name ASC, p.name ASC")
  List<ShoppingListItem> findVisibleByUserOrderByCategoryAndProductName(@Param("user") User user);

  @Query(
      "SELECT s FROM ShoppingListItem s "
          + "JOIN FETCH s.product p "
          + "JOIN FETCH p.baseUnit bu "
          + "LEFT JOIN FETCH s.checkedUnit cu "
          + "WHERE s.user = :user AND s.checked = true "
          + "ORDER BY s.addedAt ASC")
  List<ShoppingListItem> findCheckedByUserOrderByAddedAtAsc(@Param("user") User user);

  @Query(
      value =
          "WITH inserted AS ("
              + " INSERT INTO shopping_list_items"
              + " (id, user_id, product_id, is_checked, added_automatically, added_at)"
              + " SELECT gen_random_uuid(), s.user_id, s.product_id, FALSE, TRUE, NOW()"
              + " FROM stock_items s"
              + " JOIN products p ON p.id = s.product_id"
              + " WHERE s.user_id = :userId"
              + "   AND p.is_visible = TRUE"
              + "   AND s.low_threshold IS NOT NULL"
              + "   AND s.quantity <= s.low_threshold"
              + " ON CONFLICT (user_id, product_id) DO NOTHING"
              + " RETURNING product_id"
              + ")"
              + " SELECT p.id AS id, p.name AS name"
              + " FROM inserted i"
              + " JOIN products p ON p.id = i.product_id",
      nativeQuery = true)
  List<AddedProductProjection> addMissingLowThresholdItems(@Param("userId") UUID userId);

  interface AddedProductProjection {
    UUID getId();

    String getName();
  }
}
