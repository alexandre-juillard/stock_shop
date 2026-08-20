package fr.stockshop.stock_api.shoppinglist.repository;

import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.shoppinglist.entity.ShoppingListItem;
import fr.stockshop.stock_api.user.entity.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {

  boolean existsByUserAndProduct(User user, Product product);
}
