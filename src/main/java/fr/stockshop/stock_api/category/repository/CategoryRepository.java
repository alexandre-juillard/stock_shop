package fr.stockshop.stock_api.category.repository;

import fr.stockshop.stock_api.category.entity.Category;
import fr.stockshop.stock_api.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

  List<Category> findByUserOrderByNameAsc(User user);

  Optional<Category> findByIdAndUser(UUID id, User user);

  boolean existsByUserAndNameIgnoreCase(User user, String name);

  boolean existsByUserAndNameIgnoreCaseAndIdNot(User user, String name, UUID excludedId);

  @Modifying
  @Query(
      value =
          """
          delete from recipes r
          where r.user_id = :userId
            and exists (
              select 1
              from recipe_ingredients ri
              join products p on p.id = ri.product_id
              where ri.recipe_id = r.id
                and p.category_id = :categoryId
            )
          """,
      nativeQuery = true)
  int deleteRecipesLinkedToCategoryProducts(
      @Param("userId") UUID userId, @Param("categoryId") UUID categoryId);
}
