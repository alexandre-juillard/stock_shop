package fr.stockshop.stock_api.recipe.repository;

import fr.stockshop.stock_api.recipe.entity.Recipe;
import fr.stockshop.stock_api.recipe.entity.RecipeIngredient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, UUID> {

  @Query(
      "SELECT ri FROM RecipeIngredient ri "
          + "JOIN FETCH ri.product p "
          + "JOIN FETCH p.baseUnit bu "
          + "JOIN FETCH ri.unit u "
          + "WHERE ri.recipe = :recipe "
          + "ORDER BY p.name ASC, ri.id ASC")
  List<RecipeIngredient> findByRecipeOrderByProductNameAsc(@Param("recipe") Recipe recipe);

  Optional<RecipeIngredient> findByRecipeAndProduct_Id(Recipe recipe, UUID productId);

  boolean existsByRecipeAndProduct_Id(Recipe recipe, UUID productId);
}
