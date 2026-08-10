package fr.stockshop.stock_api.product.repository;

import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  boolean existsByUserAndName(User user, String name);

  boolean existsByUserAndNameAndIdNot(User user, String name, UUID excludedId);

  Optional<Product> findByIdAndUser(UUID id, User user);

  List<Product> findByUserOrderByNameAsc(User user);

  List<Product> findByUserAndCategory_IdOrderByNameAsc(User user, UUID categoryId);

  List<Product> findByUserAndVisibleOrderByNameAsc(User user, boolean visible);

  List<Product> findByUserAndCategory_IdAndVisibleOrderByNameAsc(
      User user, UUID categoryId, boolean visible);
}
