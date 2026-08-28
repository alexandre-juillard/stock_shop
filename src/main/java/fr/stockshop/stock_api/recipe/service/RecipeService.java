package fr.stockshop.stock_api.recipe.service;

import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.QuantityUnitNotFoundException;
import fr.stockshop.stock_api.exception.RecipeIngredientDuplicateProductException;
import fr.stockshop.stock_api.exception.RecipeIngredientProductNotInStockException;
import fr.stockshop.stock_api.exception.RecipeIngredientUnitMismatchException;
import fr.stockshop.stock_api.exception.RecipeNotFoundException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import fr.stockshop.stock_api.quantity.repository.QuantityUnitRepository;
import fr.stockshop.stock_api.recipe.dto.CreateRecipeIngredientRequest;
import fr.stockshop.stock_api.recipe.dto.CreateRecipeRequest;
import fr.stockshop.stock_api.recipe.dto.RecipeDetailResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeIngredientResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeProductReferenceResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeSummaryResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeUnitReferenceResponse;
import fr.stockshop.stock_api.recipe.dto.UpdateRecipeRequest;
import fr.stockshop.stock_api.recipe.entity.Recipe;
import fr.stockshop.stock_api.recipe.entity.RecipeIngredient;
import fr.stockshop.stock_api.recipe.repository.RecipeIngredientRepository;
import fr.stockshop.stock_api.recipe.repository.RecipeRepository;
import fr.stockshop.stock_api.stock.repository.StockItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeService {

  private final RecipeRepository recipeRepository;
  private final RecipeIngredientRepository recipeIngredientRepository;
  private final ProductRepository productRepository;
  private final QuantityUnitRepository quantityUnitRepository;
  private final StockItemRepository stockItemRepository;

  @Transactional
  public RecipeResponse createRecipe(User currentUser, CreateRecipeRequest request) {
    List<CreateRecipeIngredientRequest> ingredientRequests =
        request.ingredients() == null ? List.of() : request.ingredients();
    List<ResolvedRecipeIngredient> resolvedIngredients =
        resolveAndValidateIngredients(currentUser, ingredientRequests);

    Recipe savedRecipe =
        recipeRepository.save(Recipe.builder().user(currentUser).name(request.name()).build());

    if (!resolvedIngredients.isEmpty()) {
      List<RecipeIngredient> ingredientsToSave =
          resolvedIngredients.stream()
              .map(
                  resolved ->
                      RecipeIngredient.builder()
                          .recipe(savedRecipe)
                          .product(resolved.product())
                          .quantity(resolved.quantity())
                          .unit(resolved.unit())
                          .build())
              .toList();
      recipeIngredientRepository.saveAll(ingredientsToSave);
    }

    return new RecipeResponse(savedRecipe.getId(), savedRecipe.getName());
  }

  @Transactional(readOnly = true)
  public List<RecipeSummaryResponse> listRecipes(User currentUser) {
    return recipeRepository.findSummariesByUser(currentUser);
  }

  @Transactional(readOnly = true)
  public RecipeDetailResponse getRecipe(User currentUser, UUID recipeId) {
    Recipe recipe =
        recipeRepository
            .findById(recipeId)
            .orElseThrow(() -> new RecipeNotFoundException(recipeId));
    assertOwnership(recipe, currentUser);

    List<RecipeIngredientResponse> ingredients =
        recipeIngredientRepository.findByRecipeOrderByProductNameAsc(recipe).stream()
            .map(this::toIngredientResponse)
            .toList();

    return new RecipeDetailResponse(recipe.getId(), recipe.getName(), ingredients);
  }

  @Transactional
  public RecipeResponse updateRecipe(User currentUser, UUID recipeId, UpdateRecipeRequest request) {
    Recipe recipe =
        recipeRepository
            .findById(recipeId)
            .orElseThrow(() -> new RecipeNotFoundException(recipeId));
    assertOwnership(recipe, currentUser);

    recipe.setName(request.name().trim());
    Recipe savedRecipe = recipeRepository.save(recipe);
    return new RecipeResponse(savedRecipe.getId(), savedRecipe.getName());
  }

  @Transactional
  public void deleteRecipe(User currentUser, UUID recipeId) {
    Recipe recipe =
        recipeRepository
            .findById(recipeId)
            .orElseThrow(() -> new RecipeNotFoundException(recipeId));
    assertOwnership(recipe, currentUser);
    recipeRepository.delete(recipe);
  }

  @Transactional(readOnly = true)
  public List<RecipeResponse> findRecipesByIngredient(User currentUser, UUID productId) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

    assertOwnership(product, currentUser);

    List<Recipe> recipes = recipeRepository.findRecipesByProductId(productId);
    return recipes.stream()
        .map(recipe -> new RecipeResponse(recipe.getId(), recipe.getName()))
        .toList();
  }

  private List<ResolvedRecipeIngredient> resolveAndValidateIngredients(
      User currentUser, List<CreateRecipeIngredientRequest> ingredientRequests) {
    List<ResolvedRecipeIngredient> resolvedIngredients = new ArrayList<>();
    Set<UUID> seenProductIds = new HashSet<>();

    for (CreateRecipeIngredientRequest ingredientRequest : ingredientRequests) {
      if (!seenProductIds.add(ingredientRequest.productId())) {
        throw new RecipeIngredientDuplicateProductException(ingredientRequest.productId());
      }

      Product product =
          productRepository
              .findById(ingredientRequest.productId())
              .orElseThrow(() -> new ProductNotFoundException(ingredientRequest.productId()));
      assertOwnership(product, currentUser);

      if (!stockItemRepository.existsByUserAndProduct(currentUser, product)) {
        throw new RecipeIngredientProductNotInStockException(product.getId());
      }

      QuantityUnit unit =
          quantityUnitRepository
              .findById(ingredientRequest.unitId())
              .orElseThrow(() -> new QuantityUnitNotFoundException(ingredientRequest.unitId()));

      if (!product.getQuantityType().getId().equals(unit.getQuantityType().getId())) {
        throw new RecipeIngredientUnitMismatchException(product.getId(), unit.getId());
      }

      resolvedIngredients.add(
          new ResolvedRecipeIngredient(product, unit, ingredientRequest.quantity()));
    }

    return resolvedIngredients;
  }

  private RecipeIngredientResponse toIngredientResponse(RecipeIngredient ingredient) {
    return new RecipeIngredientResponse(
        new RecipeProductReferenceResponse(
            ingredient.getProduct().getId(), ingredient.getProduct().getName()),
        ingredient.getQuantity(),
        new RecipeUnitReferenceResponse(
            ingredient.getUnit().getId(),
            ingredient.getUnit().getCode(),
            ingredient.getUnit().getLabel()));
  }

  private void assertOwnership(Product product, User currentUser) {
    if (!product.getUser().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("Product does not belong to current user");
    }
  }

  private void assertOwnership(Recipe recipe, User currentUser) {
    if (!recipe.getUser().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("Recipe does not belong to current user");
    }
  }

  private record ResolvedRecipeIngredient(
      Product product, QuantityUnit unit, BigDecimal quantity) {}
}
