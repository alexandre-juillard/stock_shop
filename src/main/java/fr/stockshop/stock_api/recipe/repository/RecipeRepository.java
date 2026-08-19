package fr.stockshop.stock_api.recipe.repository;

import fr.stockshop.stock_api.recipe.entity.Recipe;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

  @Query(
      value =
          "SELECT DISTINCT r.* FROM recipes r "
              + "INNER JOIN recipe_ingredients ri ON r.id = ri.recipe_id "
              + "WHERE ri.product_id = :productId",
      nativeQuery = true)
  List<Recipe> findRecipesByProductId(@Param("productId") UUID productId);
}
