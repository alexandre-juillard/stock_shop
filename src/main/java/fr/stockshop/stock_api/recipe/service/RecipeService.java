package fr.stockshop.stock_api.recipe.service;

import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.QuantityUnitNotFoundException;
import fr.stockshop.stock_api.exception.RecipeConsumeConflictException;
import fr.stockshop.stock_api.exception.RecipeIngredientAlreadyExistsException;
import fr.stockshop.stock_api.exception.RecipeIngredientDuplicateProductException;
import fr.stockshop.stock_api.exception.RecipeIngredientNotFoundException;
import fr.stockshop.stock_api.exception.RecipeIngredientProductNotInStockException;
import fr.stockshop.stock_api.exception.RecipeIngredientUnitMismatchException;
import fr.stockshop.stock_api.exception.RecipeNotFoundException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import fr.stockshop.stock_api.quantity.repository.QuantityUnitRepository;
import fr.stockshop.stock_api.recipe.dto.CreateRecipeIngredientRequest;
import fr.stockshop.stock_api.recipe.dto.CreateRecipeRequest;
import fr.stockshop.stock_api.recipe.dto.RecipeConsumeConflictResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeConsumeInsufficientItemResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeConsumeMissingItemResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeConsumeResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeConsumeResultItemResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeDetailResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeIngredientResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeProductReferenceResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeSummaryResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeUnitReferenceResponse;
import fr.stockshop.stock_api.recipe.dto.UpdateRecipeIngredientRequest;
import fr.stockshop.stock_api.recipe.dto.UpdateRecipeRequest;
import fr.stockshop.stock_api.recipe.entity.Recipe;
import fr.stockshop.stock_api.recipe.entity.RecipeIngredient;
import fr.stockshop.stock_api.recipe.repository.RecipeIngredientRepository;
import fr.stockshop.stock_api.recipe.repository.RecipeRepository;
import fr.stockshop.stock_api.shoppinglist.entity.ShoppingListItem;
import fr.stockshop.stock_api.shoppinglist.repository.ShoppingListItemRepository;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.repository.StockItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeService {

  private static final int STOCK_QUANTITY_SCALE = 3;

  private final RecipeRepository recipeRepository;
  private final RecipeIngredientRepository recipeIngredientRepository;
  private final ProductRepository productRepository;
  private final QuantityUnitRepository quantityUnitRepository;
  private final StockItemRepository stockItemRepository;
  private final ShoppingListItemRepository shoppingListItemRepository;

  @Transactional
  public RecipeResponse createRecipe(User currentUser, CreateRecipeRequest request) {
    List<CreateRecipeIngredientRequest> ingredientRequests = request.ingredients();
    List<ResolvedRecipeIngredient> resolvedIngredients =
        resolveAndValidateIngredients(currentUser, ingredientRequests);

    Recipe savedRecipe =
        recipeRepository.save(
            Recipe.builder().user(currentUser).name(request.name().trim()).build());

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
    Recipe recipe = resolveOwnedRecipe(currentUser, recipeId);

    List<RecipeIngredientResponse> ingredients =
        recipeIngredientRepository.findByRecipeOrderByProductNameAsc(recipe).stream()
            .map(this::toIngredientResponse)
            .toList();

    return new RecipeDetailResponse(recipe.getId(), recipe.getName(), ingredients);
  }

  @Transactional
  public RecipeResponse updateRecipe(User currentUser, UUID recipeId, UpdateRecipeRequest request) {
    Recipe recipe = resolveOwnedRecipe(currentUser, recipeId);

    recipe.setName(request.name().trim());
    Recipe savedRecipe = recipeRepository.save(recipe);
    return new RecipeResponse(savedRecipe.getId(), savedRecipe.getName());
  }

  @Transactional
  public void deleteRecipe(User currentUser, UUID recipeId) {
    Recipe recipe = resolveOwnedRecipe(currentUser, recipeId);
    recipeRepository.delete(recipe);
  }

  @Transactional
  public RecipeIngredientResponse addIngredient(
      User currentUser, UUID recipeId, CreateRecipeIngredientRequest request) {
    Recipe recipe = resolveOwnedRecipe(currentUser, recipeId);
    Product product = resolveOwnedProduct(currentUser, request.productId());

    if (!stockItemRepository.existsByUserAndProduct(currentUser, product)) {
      throw new RecipeIngredientProductNotInStockException(product.getId());
    }

    if (recipeIngredientRepository.existsByRecipeAndProduct_Id(recipe, product.getId())) {
      throw new RecipeIngredientAlreadyExistsException(product.getId());
    }

    QuantityUnit unit = resolveCompatibleUnit(product, request.unitId());

    try {
      RecipeIngredient savedIngredient =
          recipeIngredientRepository.saveAndFlush(
              RecipeIngredient.builder()
                  .recipe(recipe)
                  .product(product)
                  .quantity(request.quantity())
                  .unit(unit)
                  .build());
      return toIngredientResponse(savedIngredient);
    } catch (DataIntegrityViolationException ex) {
      throw new RecipeIngredientAlreadyExistsException(product.getId());
    }
  }

  @Transactional
  public RecipeIngredientResponse updateIngredient(
      User currentUser, UUID recipeId, UUID productId, UpdateRecipeIngredientRequest request) {
    Recipe recipe = resolveOwnedRecipe(currentUser, recipeId);
    RecipeIngredient ingredient = resolveRecipeIngredient(recipe, productId);

    QuantityUnit unit = resolveCompatibleUnit(ingredient.getProduct(), request.unitId());
    ingredient.setQuantity(request.quantity());
    ingredient.setUnit(unit);

    RecipeIngredient savedIngredient = recipeIngredientRepository.save(ingredient);
    return toIngredientResponse(savedIngredient);
  }

  @Transactional
  public void deleteIngredient(User currentUser, UUID recipeId, UUID productId) {
    Recipe recipe = resolveOwnedRecipe(currentUser, recipeId);
    RecipeIngredient ingredient = resolveRecipeIngredient(recipe, productId);
    recipeIngredientRepository.delete(ingredient);
  }

  @Transactional
  public RecipeConsumeResponse consumeRecipe(User currentUser, UUID recipeId, boolean force) {
    Recipe recipe = resolveOwnedRecipe(currentUser, recipeId);
    List<RecipeIngredient> ingredients =
        recipeIngredientRepository.findByRecipeOrderByProductNameAsc(recipe);

    if (ingredients.isEmpty()) {
      return new RecipeConsumeResponse(List.of());
    }

    List<UUID> productIds =
        ingredients.stream().map(ingredient -> ingredient.getProduct().getId()).toList();
    Map<UUID, StockItem> stockByProductId = new HashMap<>();
    stockItemRepository
        .findByUserAndProduct_IdIn(currentUser, productIds)
        .forEach(stockItem -> stockByProductId.put(stockItem.getProduct().getId(), stockItem));

    List<RecipeConsumeMissingItemResponse> missing = new ArrayList<>();
    List<RecipeConsumeInsufficientItemResponse> insufficient = new ArrayList<>();
    List<ConsumptionPlanLine> lines = new ArrayList<>();

    for (RecipeIngredient ingredient : ingredients) {
      Product product = ingredient.getProduct();
      BigDecimal required = toBaseUnitQuantity(ingredient);

      StockItem stockItem = stockByProductId.get(product.getId());
      if (stockItem == null) {
        missing.add(new RecipeConsumeMissingItemResponse(product.getId(), product.getName()));
        continue;
      }

      BigDecimal available = normalizeStockQuantity(stockItem.getQuantity());
      boolean hasEnough = available.compareTo(required) >= 0;
      if (!hasEnough) {
        insufficient.add(
            new RecipeConsumeInsufficientItemResponse(
                product.getId(), product.getName(), required, available));
      }

      BigDecimal deducted;
      BigDecimal newStockQuantity;
      boolean forced;

      if (hasEnough) {
        deducted = required;
        newStockQuantity = normalizeStockQuantity(available.subtract(required));
        forced = false;
      } else if (force) {
        deducted = available;
        newStockQuantity = BigDecimal.ZERO.setScale(STOCK_QUANTITY_SCALE, RoundingMode.HALF_UP);
        forced = true;
      } else {
        deducted = BigDecimal.ZERO.setScale(STOCK_QUANTITY_SCALE, RoundingMode.HALF_UP);
        newStockQuantity = available;
        forced = false;
      }

      lines.add(new ConsumptionPlanLine(product, stockItem, deducted, newStockQuantity, forced));
    }

    if (!force && (!missing.isEmpty() || !insufficient.isEmpty())) {
      throw new RecipeConsumeConflictException(
          new RecipeConsumeConflictResponse(missing, insufficient));
    }

    List<RecipeConsumeResultItemResponse> results = new ArrayList<>();
    for (ConsumptionPlanLine line : lines) {
      line.stockItem().setQuantity(line.newStockQuantity());
      applyLowThresholdRule(currentUser, line.stockItem());

      results.add(
          new RecipeConsumeResultItemResponse(
              line.product().getId(),
              line.product().getName(),
              line.deducted(),
              line.newStockQuantity(),
              line.forced()));
    }

    return new RecipeConsumeResponse(results);
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

      Product product = resolveOwnedProduct(currentUser, ingredientRequest.productId());

      if (!stockItemRepository.existsByUserAndProduct(currentUser, product)) {
        throw new RecipeIngredientProductNotInStockException(product.getId());
      }

      QuantityUnit unit = resolveCompatibleUnit(product, ingredientRequest.unitId());

      resolvedIngredients.add(
          new ResolvedRecipeIngredient(product, unit, ingredientRequest.quantity()));
    }

    return resolvedIngredients;
  }

  private BigDecimal toBaseUnitQuantity(RecipeIngredient ingredient) {
    BigDecimal quantityInReference =
        ingredient.getQuantity().multiply(ingredient.getUnit().getConversionFactor());
    return quantityInReference.divide(
        ingredient.getProduct().getBaseUnit().getConversionFactor(),
        STOCK_QUANTITY_SCALE,
        RoundingMode.HALF_UP);
  }

  private BigDecimal normalizeStockQuantity(BigDecimal quantity) {
    return quantity.setScale(STOCK_QUANTITY_SCALE, RoundingMode.HALF_UP);
  }

  private void applyLowThresholdRule(User currentUser, StockItem stockItem) {
    boolean isBelowThreshold =
        stockItem.getLowThreshold() != null
            && stockItem.getQuantity().compareTo(stockItem.getLowThreshold()) <= 0;

    if (isBelowThreshold
        && !shoppingListItemRepository.existsByUserAndProduct(
            currentUser, stockItem.getProduct())) {
      shoppingListItemRepository.save(
          ShoppingListItem.builder()
              .user(currentUser)
              .product(stockItem.getProduct())
              .checked(false)
              .addedAutomatically(true)
              .build());
    }
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

  private Recipe resolveOwnedRecipe(User currentUser, UUID recipeId) {
    Recipe recipe =
        recipeRepository
            .findById(recipeId)
            .orElseThrow(() -> new RecipeNotFoundException(recipeId));
    assertOwnership(recipe, currentUser);
    return recipe;
  }

  private RecipeIngredient resolveRecipeIngredient(Recipe recipe, UUID productId) {
    return recipeIngredientRepository
        .findByRecipeAndProduct_Id(recipe, productId)
        .orElseThrow(() -> new RecipeIngredientNotFoundException(productId));
  }

  private Product resolveOwnedProduct(User currentUser, UUID productId) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    assertOwnership(product, currentUser);
    return product;
  }

  private QuantityUnit resolveCompatibleUnit(Product product, UUID unitId) {
    QuantityUnit unit =
        quantityUnitRepository
            .findById(unitId)
            .orElseThrow(() -> new QuantityUnitNotFoundException(unitId));

    if (!product.getQuantityType().getId().equals(unit.getQuantityType().getId())) {
      throw new RecipeIngredientUnitMismatchException(product.getId(), unit.getId());
    }

    return unit;
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

  private record ConsumptionPlanLine(
      Product product,
      StockItem stockItem,
      BigDecimal deducted,
      BigDecimal newStockQuantity,
      boolean forced) {}

  private record ResolvedRecipeIngredient(
      Product product, QuantityUnit unit, BigDecimal quantity) {}
}
