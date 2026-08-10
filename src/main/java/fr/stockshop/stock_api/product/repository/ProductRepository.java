package fr.stockshop.stock_api.product.repository;

import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.user.entity.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  boolean existsByUserAndName(User user, String name);
}
