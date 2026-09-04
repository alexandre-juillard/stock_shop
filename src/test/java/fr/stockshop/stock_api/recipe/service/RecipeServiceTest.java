package fr.stockshop.stock_api.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.RecipeConsumeConflictException;
import fr.stockshop.stock_api.exception.RecipeIngredientAlreadyExistsException;
import fr.stockshop.stock_api.exception.RecipeIngredientNotFoundException;
import fr.stockshop.stock_api.exception.RecipeIngredientProductNotInStockException;
import fr.stockshop.stock_api.exception.RecipeIngredientUnitMismatchException;
import fr.stockshop.stock_api.exception.RecipeNotFoundException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.quantity.entity.QuantityType;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import fr.stockshop.stock_api.quantity.repository.QuantityUnitRepository;
import fr.stockshop.stock_api.recipe.dto.CreateRecipeIngredientRequest;
import fr.stockshop.stock_api.recipe.dto.CreateRecipeRequest;
import fr.stockshop.stock_api.recipe.dto.RecipeConsumeConflictResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeConsumeResponse;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

  @Mock private RecipeRepository recipeRepository;
  @Mock private RecipeIngredientRepository recipeIngredientRepository;
  @Mock private ProductRepository productRepository;
  @Mock private QuantityUnitRepository quantityUnitRepository;
  @Mock private StockItemRepository stockItemRepository;
  @Mock private ShoppingListItemRepository shoppingListItemRepository;

  @InjectMocks private RecipeService recipeService;

  @Test
  void createRecipeReturnsCreatedPayloadWhenNameOnly() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).build();

    CreateRecipeRequest request = new CreateRecipeRequest("Pesto", null);

    Recipe savedRecipe = Recipe.builder().id(recipeId).name("Pesto").build();
    when(recipeRepository.save(any(Recipe.class))).thenReturn(savedRecipe);

    RecipeResponse result = recipeService.createRecipe(currentUser, request);

    assertThat(result).isEqualTo(new RecipeResponse(recipeId, "Pesto"));
    verify(recipeRepository).save(any(Recipe.class));
    verifyNoInteractions(
        recipeIngredientRepository,
        productRepository,
        quantityUnitRepository,
        stockItemRepository,
        shoppingListItemRepository);
  }

  @Test
  void createRecipePersistsIngredientsWhenReferencesAreValid() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    QuantityType quantityType = QuantityType.builder().id(UUID.randomUUID()).build();

    Product product =
        Product.builder()
            .id(productId)
            .name("Basilic")
            .user(User.builder().id(userId).build())
            .quantityType(quantityType)
            .build();
    QuantityUnit unit =
        QuantityUnit.builder()
            .id(unitId)
            .code("kg")
            .label("Kilogramme")
            .quantityType(quantityType)
            .build();

    CreateRecipeRequest request =
        new CreateRecipeRequest(
            "Pesto",
            List.of(new CreateRecipeIngredientRequest(productId, new BigDecimal("0.250"), unitId)));

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(true);
    when(quantityUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
    when(recipeRepository.save(any(Recipe.class)))
        .thenReturn(Recipe.builder().id(recipeId).name("Pesto").build());

    RecipeResponse result = recipeService.createRecipe(currentUser, request);

    assertThat(result).isEqualTo(new RecipeResponse(recipeId, "Pesto"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<RecipeIngredient>> ingredientsCaptor = ArgumentCaptor.forClass(List.class);
    verify(recipeIngredientRepository).saveAll(ingredientsCaptor.capture());

    List<RecipeIngredient> savedIngredients = ingredientsCaptor.getValue();
    assertThat(savedIngredients).hasSize(1);
    RecipeIngredient ingredient = savedIngredients.get(0);
    assertThat(ingredient.getRecipe().getId()).isEqualTo(recipeId);
    assertThat(ingredient.getProduct().getId()).isEqualTo(productId);
    assertThat(ingredient.getUnit().getId()).isEqualTo(unitId);
    assertThat(ingredient.getQuantity()).isEqualByComparingTo("0.250");
  }

  @Test
  void createRecipeThrowsBadRequestWhenProductIsNotInStock() {
    UUID userId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).build();

    Product product =
        Product.builder()
            .id(productId)
            .user(User.builder().id(userId).build())
            .quantityType(QuantityType.builder().id(UUID.randomUUID()).build())
            .build();

    CreateRecipeRequest request =
        new CreateRecipeRequest(
            "Tarte",
            List.of(new CreateRecipeIngredientRequest(productId, new BigDecimal("1.0"), unitId)));

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);

    assertThatThrownBy(() -> recipeService.createRecipe(currentUser, request))
        .isInstanceOf(RecipeIngredientProductNotInStockException.class);

    verifyNoInteractions(quantityUnitRepository, recipeIngredientRepository);
  }

  @Test
  void listRecipesReturnsSummaryDtosFromRepository() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    List<RecipeSummaryResponse> expected =
        List.of(new RecipeSummaryResponse(UUID.randomUUID(), "Soupe", 2, Instant.now()));

    when(recipeRepository.findSummariesByUser(currentUser)).thenReturn(expected);

    List<RecipeSummaryResponse> result = recipeService.listRecipes(currentUser);

    assertThat(result).containsExactlyElementsOf(expected);
  }

  @Test
  void getRecipeReturnsDetailWithIngredients() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    Recipe recipe =
        Recipe.builder()
            .id(recipeId)
            .name("Gratin")
            .user(User.builder().id(userId).build())
            .build();
    RecipeIngredient ingredient =
        RecipeIngredient.builder()
            .recipe(recipe)
            .product(Product.builder().id(productId).name("Pomme de terre").build())
            .unit(QuantityUnit.builder().id(unitId).code("kg").label("Kilogramme").build())
            .quantity(new BigDecimal("1.500"))
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(recipeIngredientRepository.findByRecipeOrderByProductNameAsc(recipe))
        .thenReturn(List.of(ingredient));

    RecipeDetailResponse result = recipeService.getRecipe(currentUser, recipeId);

    assertThat(result)
        .isEqualTo(
            new RecipeDetailResponse(
                recipeId,
                "Gratin",
                List.of(
                    new RecipeIngredientResponse(
                        new RecipeProductReferenceResponse(productId, "Pomme de terre"),
                        new BigDecimal("1.500"),
                        new RecipeUnitReferenceResponse(unitId, "kg", "Kilogramme")))));
  }

  @Test
  void getRecipeThrowsForbiddenWhenOwnedByAnotherUser() {
    UUID recipeId = UUID.randomUUID();
    Recipe recipe =
        Recipe.builder()
            .id(recipeId)
            .name("Privee")
            .user(User.builder().id(UUID.randomUUID()).build())
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));

    assertThatThrownBy(
            () -> recipeService.getRecipe(User.builder().id(UUID.randomUUID()).build(), recipeId))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(recipeIngredientRepository);
  }

  @Test
  void getRecipeThrowsNotFoundWhenMissing() {
    UUID recipeId = UUID.randomUUID();
    when(recipeRepository.findById(recipeId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> recipeService.getRecipe(User.builder().id(UUID.randomUUID()).build(), recipeId))
        .isInstanceOf(RecipeNotFoundException.class);

    verifyNoInteractions(recipeIngredientRepository);
  }

  @Test
  void updateRecipeReturnsUpdatedPayloadWhenOwnedByCurrentUser() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    Recipe recipe =
        Recipe.builder()
            .id(recipeId)
            .name("Ancien nom")
            .user(User.builder().id(userId).build())
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(recipeRepository.save(recipe)).thenReturn(recipe);

    RecipeResponse result =
        recipeService.updateRecipe(
            currentUser, recipeId, new UpdateRecipeRequest("  Nouveau nom  "));

    assertThat(result).isEqualTo(new RecipeResponse(recipeId, "Nouveau nom"));
    assertThat(recipe.getName()).isEqualTo("Nouveau nom");
    verify(recipeRepository).save(recipe);
  }

  @Test
  void updateRecipeThrowsForbiddenWhenOwnedByAnotherUser() {
    UUID recipeId = UUID.randomUUID();
    Recipe recipe =
        Recipe.builder()
            .id(recipeId)
            .name("Privee")
            .user(User.builder().id(UUID.randomUUID()).build())
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));

    assertThatThrownBy(
            () ->
                recipeService.updateRecipe(
                    User.builder().id(UUID.randomUUID()).build(),
                    recipeId,
                    new UpdateRecipeRequest("Nom")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void updateRecipeThrowsNotFoundWhenMissing() {
    UUID recipeId = UUID.randomUUID();
    when(recipeRepository.findById(recipeId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                recipeService.updateRecipe(
                    User.builder().id(UUID.randomUUID()).build(),
                    recipeId,
                    new UpdateRecipeRequest("Nom")))
        .isInstanceOf(RecipeNotFoundException.class);
  }

  @Test
  void deleteRecipeRemovesOwnedRecipe() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).build();
    Recipe recipe =
        Recipe.builder()
            .id(recipeId)
            .name("A supprimer")
            .user(User.builder().id(userId).build())
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));

    recipeService.deleteRecipe(currentUser, recipeId);

    verify(recipeRepository).delete(recipe);
  }

  @Test
  void deleteRecipeThrowsForbiddenWhenOwnedByAnotherUser() {
    UUID recipeId = UUID.randomUUID();
    Recipe recipe =
        Recipe.builder()
            .id(recipeId)
            .name("Privee")
            .user(User.builder().id(UUID.randomUUID()).build())
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));

    assertThatThrownBy(
            () ->
                recipeService.deleteRecipe(User.builder().id(UUID.randomUUID()).build(), recipeId))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void deleteRecipeThrowsNotFoundWhenMissing() {
    UUID recipeId = UUID.randomUUID();
    when(recipeRepository.findById(recipeId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                recipeService.deleteRecipe(User.builder().id(UUID.randomUUID()).build(), recipeId))
        .isInstanceOf(RecipeNotFoundException.class);
  }

  @Test
  void addIngredientCreatesRecipeIngredientWhenReferencesAreValid() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    QuantityType quantityType = QuantityType.builder().id(UUID.randomUUID()).build();
    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();
    Product product =
        Product.builder()
            .id(productId)
            .name("Basilic")
            .user(User.builder().id(userId).build())
            .quantityType(quantityType)
            .build();
    QuantityUnit unit =
        QuantityUnit.builder()
            .id(unitId)
            .code("kg")
            .label("Kilogramme")
            .quantityType(quantityType)
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(true);
    when(recipeIngredientRepository.existsByRecipeAndProduct_Id(recipe, productId))
        .thenReturn(false);
    when(quantityUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
    when(recipeIngredientRepository.saveAndFlush(any(RecipeIngredient.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    RecipeIngredientResponse result =
        recipeService.addIngredient(
            currentUser,
            recipeId,
            new CreateRecipeIngredientRequest(productId, new BigDecimal("0.250"), unitId));

    assertThat(result)
        .isEqualTo(
            new RecipeIngredientResponse(
                new RecipeProductReferenceResponse(productId, "Basilic"),
                new BigDecimal("0.250"),
                new RecipeUnitReferenceResponse(unitId, "kg", "Kilogramme")));
  }

  @Test
  void addIngredientThrowsConflictWhenIngredientAlreadyExists() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    QuantityType quantityType = QuantityType.builder().id(UUID.randomUUID()).build();
    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();
    Product product =
        Product.builder()
            .id(productId)
            .user(User.builder().id(userId).build())
            .quantityType(quantityType)
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(true);
    when(recipeIngredientRepository.existsByRecipeAndProduct_Id(recipe, productId))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                recipeService.addIngredient(
                    currentUser,
                    recipeId,
                    new CreateRecipeIngredientRequest(productId, new BigDecimal("1.000"), unitId)))
        .isInstanceOf(RecipeIngredientAlreadyExistsException.class);
  }

  @Test
  void addIngredientThrowsBadRequestWhenUnitIsIncompatible() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    QuantityType productType = QuantityType.builder().id(UUID.randomUUID()).build();
    QuantityType otherType = QuantityType.builder().id(UUID.randomUUID()).build();

    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();
    Product product =
        Product.builder()
            .id(productId)
            .user(User.builder().id(userId).build())
            .quantityType(productType)
            .build();
    QuantityUnit incompatibleUnit =
        QuantityUnit.builder().id(unitId).quantityType(otherType).build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(true);
    when(recipeIngredientRepository.existsByRecipeAndProduct_Id(recipe, productId))
        .thenReturn(false);
    when(quantityUnitRepository.findById(unitId)).thenReturn(Optional.of(incompatibleUnit));

    assertThatThrownBy(
            () ->
                recipeService.addIngredient(
                    currentUser,
                    recipeId,
                    new CreateRecipeIngredientRequest(productId, new BigDecimal("1.000"), unitId)))
        .isInstanceOf(RecipeIngredientUnitMismatchException.class);
  }

  @Test
  void updateIngredientUpdatesQuantityAndUnitWhenOwnedByCurrentUser() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID currentUnitId = UUID.randomUUID();
    UUID newUnitId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    QuantityType quantityType = QuantityType.builder().id(UUID.randomUUID()).build();
    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();
    Product product =
        Product.builder()
            .id(productId)
            .name("Tomate")
            .quantityType(quantityType)
            .user(User.builder().id(userId).build())
            .build();
    RecipeIngredient ingredient =
        RecipeIngredient.builder()
            .recipe(recipe)
            .product(product)
            .quantity(new BigDecimal("0.200"))
            .unit(QuantityUnit.builder().id(currentUnitId).quantityType(quantityType).build())
            .build();
    QuantityUnit newUnit =
        QuantityUnit.builder()
            .id(newUnitId)
            .code("g")
            .label("Gramme")
            .quantityType(quantityType)
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(recipeIngredientRepository.findByRecipeAndProduct_Id(recipe, productId))
        .thenReturn(Optional.of(ingredient));
    when(quantityUnitRepository.findById(newUnitId)).thenReturn(Optional.of(newUnit));
    when(recipeIngredientRepository.save(ingredient)).thenReturn(ingredient);

    RecipeIngredientResponse result =
        recipeService.updateIngredient(
            currentUser,
            recipeId,
            productId,
            new UpdateRecipeIngredientRequest(new BigDecimal("0.500"), newUnitId));

    assertThat(ingredient.getQuantity()).isEqualByComparingTo("0.500");
    assertThat(ingredient.getUnit().getId()).isEqualTo(newUnitId);
    assertThat(result)
        .isEqualTo(
            new RecipeIngredientResponse(
                new RecipeProductReferenceResponse(productId, "Tomate"),
                new BigDecimal("0.500"),
                new RecipeUnitReferenceResponse(newUnitId, "g", "Gramme")));
  }

  @Test
  void updateIngredientThrowsNotFoundWhenIngredientIsMissing() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(recipeIngredientRepository.findByRecipeAndProduct_Id(recipe, productId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                recipeService.updateIngredient(
                    currentUser,
                    recipeId,
                    productId,
                    new UpdateRecipeIngredientRequest(new BigDecimal("1.000"), UUID.randomUUID())))
        .isInstanceOf(RecipeIngredientNotFoundException.class);
  }

  @Test
  void deleteIngredientRemovesIngredientOnly() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();
    RecipeIngredient ingredient =
        RecipeIngredient.builder()
            .recipe(recipe)
            .product(Product.builder().id(productId).build())
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(recipeIngredientRepository.findByRecipeAndProduct_Id(recipe, productId))
        .thenReturn(Optional.of(ingredient));

    recipeService.deleteIngredient(currentUser, recipeId, productId);

    verify(recipeIngredientRepository).delete(ingredient);
  }

  @Test
  void deleteIngredientThrowsNotFoundWhenIngredientMissing() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(recipeIngredientRepository.findByRecipeAndProduct_Id(recipe, productId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> recipeService.deleteIngredient(currentUser, recipeId, productId))
        .isInstanceOf(RecipeIngredientNotFoundException.class);
  }

  @Test
  void consumeRecipeReturnsConflictWithMissingItemsWhenForceDisabled() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    QuantityType quantityType = QuantityType.builder().id(UUID.randomUUID()).build();
    QuantityUnit kgUnit =
        QuantityUnit.builder()
            .id(UUID.randomUUID())
            .code("kg")
            .label("Kilogramme")
            .quantityType(quantityType)
            .conversionFactor(BigDecimal.ONE)
            .baseUnit(true)
            .build();
    Product product =
        Product.builder()
            .id(productId)
            .name("Farine")
            .user(User.builder().id(userId).build())
            .quantityType(quantityType)
            .baseUnit(kgUnit)
            .build();
    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();
    RecipeIngredient ingredient =
        RecipeIngredient.builder()
            .recipe(recipe)
            .product(product)
            .unit(kgUnit)
            .quantity(new BigDecimal("0.500"))
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(recipeIngredientRepository.findByRecipeOrderByProductNameAsc(recipe))
        .thenReturn(List.of(ingredient));
    when(stockItemRepository.findByUserAndProduct_IdIn(currentUser, List.of(productId)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> recipeService.consumeRecipe(currentUser, recipeId, false))
        .isInstanceOf(RecipeConsumeConflictException.class)
        .satisfies(
            throwable -> {
              RecipeConsumeConflictResponse conflict =
                  ((RecipeConsumeConflictException) throwable).getConflict();
              assertThat(conflict.missing()).hasSize(1);
              assertThat(conflict.missing().get(0).productId()).isEqualTo(productId);
              assertThat(conflict.insufficient()).isEmpty();
            });

    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void consumeRecipeReturnsConflictWithInsufficientItemsWhenForceDisabled() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    QuantityType quantityType = QuantityType.builder().id(UUID.randomUUID()).build();
    QuantityUnit kgUnit =
        QuantityUnit.builder()
            .id(UUID.randomUUID())
            .code("kg")
            .label("Kilogramme")
            .quantityType(quantityType)
            .conversionFactor(BigDecimal.ONE)
            .baseUnit(true)
            .build();
    Product product =
        Product.builder()
            .id(productId)
            .name("Riz")
            .user(User.builder().id(userId).build())
            .quantityType(quantityType)
            .baseUnit(kgUnit)
            .build();
    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();
    RecipeIngredient ingredient =
        RecipeIngredient.builder()
            .recipe(recipe)
            .product(product)
            .unit(kgUnit)
            .quantity(new BigDecimal("0.500"))
            .build();
    StockItem stockItem =
        StockItem.builder()
            .id(UUID.randomUUID())
            .user(currentUser)
            .product(product)
            .quantity(new BigDecimal("0.200"))
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(recipeIngredientRepository.findByRecipeOrderByProductNameAsc(recipe))
        .thenReturn(List.of(ingredient));
    when(stockItemRepository.findByUserAndProduct_IdIn(currentUser, List.of(productId)))
        .thenReturn(List.of(stockItem));

    assertThatThrownBy(() -> recipeService.consumeRecipe(currentUser, recipeId, false))
        .isInstanceOf(RecipeConsumeConflictException.class)
        .satisfies(
            throwable -> {
              RecipeConsumeConflictResponse conflict =
                  ((RecipeConsumeConflictException) throwable).getConflict();
              assertThat(conflict.missing()).isEmpty();
              assertThat(conflict.insufficient()).hasSize(1);
              assertThat(conflict.insufficient().get(0).productId()).isEqualTo(productId);
              assertThat(conflict.insufficient().get(0).required()).isEqualByComparingTo("0.500");
              assertThat(conflict.insufficient().get(0).available()).isEqualByComparingTo("0.200");
            });

    assertThat(stockItem.getQuantity()).isEqualByComparingTo("0.200");
    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void consumeRecipeDeductsStockWithConversionAndReevaluatesThreshold() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    QuantityType quantityType = QuantityType.builder().id(UUID.randomUUID()).build();
    QuantityUnit kgUnit =
        QuantityUnit.builder()
            .id(UUID.randomUUID())
            .code("kg")
            .label("Kilogramme")
            .quantityType(quantityType)
            .conversionFactor(BigDecimal.ONE)
            .baseUnit(true)
            .build();
    QuantityUnit gramUnit =
        QuantityUnit.builder()
            .id(UUID.randomUUID())
            .code("g")
            .label("Gramme")
            .quantityType(quantityType)
            .conversionFactor(new BigDecimal("0.001"))
            .baseUnit(false)
            .build();
    Product product =
        Product.builder()
            .id(productId)
            .name("Sucre")
            .user(User.builder().id(userId).build())
            .quantityType(quantityType)
            .baseUnit(kgUnit)
            .build();
    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();
    RecipeIngredient ingredient =
        RecipeIngredient.builder()
            .recipe(recipe)
            .product(product)
            .unit(gramUnit)
            .quantity(new BigDecimal("500.000"))
            .build();
    StockItem stockItem =
        StockItem.builder()
            .id(UUID.randomUUID())
            .user(currentUser)
            .product(product)
            .quantity(new BigDecimal("2.000"))
            .lowThreshold(new BigDecimal("1.600"))
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(recipeIngredientRepository.findByRecipeOrderByProductNameAsc(recipe))
        .thenReturn(List.of(ingredient));
    when(stockItemRepository.findByUserAndProduct_IdIn(currentUser, List.of(productId)))
        .thenReturn(List.of(stockItem));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);

    RecipeConsumeResponse response = recipeService.consumeRecipe(currentUser, recipeId, false);

    assertThat(response.results()).hasSize(1);
    assertThat(response.results().get(0).productId()).isEqualTo(productId);
    assertThat(response.results().get(0).deducted()).isEqualByComparingTo("0.500");
    assertThat(response.results().get(0).newStockQuantity()).isEqualByComparingTo("1.500");
    assertThat(response.results().get(0).forced()).isFalse();
    assertThat(stockItem.getQuantity()).isEqualByComparingTo("1.500");

    verify(shoppingListItemRepository).save(any(ShoppingListItem.class));
  }

  @Test
  void consumeRecipeWithForceHandlesSufficientInsufficientAndMissingTogether() {
    UUID userId = UUID.randomUUID();
    UUID recipeId = UUID.randomUUID();
    UUID productAId = UUID.randomUUID();
    UUID productBId = UUID.randomUUID();
    UUID productCId = UUID.randomUUID();

    User currentUser = User.builder().id(userId).build();
    QuantityType quantityType = QuantityType.builder().id(UUID.randomUUID()).build();
    QuantityUnit kgUnit =
        QuantityUnit.builder()
            .id(UUID.randomUUID())
            .code("kg")
            .label("Kilogramme")
            .quantityType(quantityType)
            .conversionFactor(BigDecimal.ONE)
            .baseUnit(true)
            .build();

    Product productA =
        Product.builder()
            .id(productAId)
            .name("Tomate")
            .user(User.builder().id(userId).build())
            .quantityType(quantityType)
            .baseUnit(kgUnit)
            .build();
    Product productB =
        Product.builder()
            .id(productBId)
            .name("Sel")
            .user(User.builder().id(userId).build())
            .quantityType(quantityType)
            .baseUnit(kgUnit)
            .build();
    Product productC =
        Product.builder()
            .id(productCId)
            .name("Poivre")
            .user(User.builder().id(userId).build())
            .quantityType(quantityType)
            .baseUnit(kgUnit)
            .build();

    Recipe recipe = Recipe.builder().id(recipeId).user(User.builder().id(userId).build()).build();
    RecipeIngredient ingredientA =
        RecipeIngredient.builder()
            .recipe(recipe)
            .product(productA)
            .unit(kgUnit)
            .quantity(new BigDecimal("0.500"))
            .build();
    RecipeIngredient ingredientB =
        RecipeIngredient.builder()
            .recipe(recipe)
            .product(productB)
            .unit(kgUnit)
            .quantity(new BigDecimal("0.500"))
            .build();
    RecipeIngredient ingredientC =
        RecipeIngredient.builder()
            .recipe(recipe)
            .product(productC)
            .unit(kgUnit)
            .quantity(new BigDecimal("0.500"))
            .build();

    StockItem stockA =
        StockItem.builder()
            .id(UUID.randomUUID())
            .user(currentUser)
            .product(productA)
            .quantity(new BigDecimal("1.000"))
            .build();
    StockItem stockB =
        StockItem.builder()
            .id(UUID.randomUUID())
            .user(currentUser)
            .product(productB)
            .quantity(new BigDecimal("0.200"))
            .lowThreshold(new BigDecimal("0.500"))
            .build();

    when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
    when(recipeIngredientRepository.findByRecipeOrderByProductNameAsc(recipe))
        .thenReturn(List.of(ingredientA, ingredientB, ingredientC));
    when(stockItemRepository.findByUserAndProduct_IdIn(
            currentUser, List.of(productAId, productBId, productCId)))
        .thenReturn(List.of(stockA, stockB));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, productB))
        .thenReturn(false);

    RecipeConsumeResponse response = recipeService.consumeRecipe(currentUser, recipeId, true);

    assertThat(response.results()).hasSize(2);

    assertThat(response.results().get(0).productId()).isEqualTo(productAId);
    assertThat(response.results().get(0).deducted()).isEqualByComparingTo("0.500");
    assertThat(response.results().get(0).newStockQuantity()).isEqualByComparingTo("0.500");
    assertThat(response.results().get(0).forced()).isFalse();

    assertThat(response.results().get(1).productId()).isEqualTo(productBId);
    assertThat(response.results().get(1).deducted()).isEqualByComparingTo("0.200");
    assertThat(response.results().get(1).newStockQuantity()).isEqualByComparingTo("0.000");
    assertThat(response.results().get(1).forced()).isTrue();

    assertThat(stockA.getQuantity()).isEqualByComparingTo("0.500");
    assertThat(stockB.getQuantity()).isEqualByComparingTo("0.000");

    verify(shoppingListItemRepository).save(any(ShoppingListItem.class));
  }

  @Test
  void findRecipesByIngredientReturnsMappedRecipesWhenProductIsOwned() {
    UUID userId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).build();
    Product product =
        Product.builder().id(productId).user(User.builder().id(userId).build()).build();

    Recipe recipe1 = Recipe.builder().id(UUID.randomUUID()).name("Pesto").build();
    Recipe recipe2 = Recipe.builder().id(UUID.randomUUID()).name("Salade de pates").build();

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(recipeRepository.findRecipesByProductId(productId)).thenReturn(List.of(recipe1, recipe2));

    List<RecipeResponse> result = recipeService.findRecipesByIngredient(currentUser, productId);

    assertThat(result)
        .containsExactly(
            new RecipeResponse(recipe1.getId(), "Pesto"),
            new RecipeResponse(recipe2.getId(), "Salade de pates"));
    verify(recipeRepository).findRecipesByProductId(productId);
  }

  @Test
  void findRecipesByIngredientReturnsEmptyListWhenNoRecipeContainsIngredient() {
    UUID userId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).build();
    Product product =
        Product.builder().id(productId).user(User.builder().id(userId).build()).build();

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(recipeRepository.findRecipesByProductId(productId)).thenReturn(List.of());

    List<RecipeResponse> result = recipeService.findRecipesByIngredient(currentUser, productId);

    assertThat(result).isEmpty();
    verify(recipeRepository).findRecipesByProductId(productId);
  }

  @Test
  void findRecipesByIngredientThrowsForbiddenWhenProductIsOwnedByAnotherUser() {
    UUID productOwnerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    User currentUser = User.builder().id(requesterId).build();
    Product product =
        Product.builder().id(productId).user(User.builder().id(productOwnerId).build()).build();

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> recipeService.findRecipesByIngredient(currentUser, productId))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(recipeRepository);
  }

  @Test
  void findRecipesByIngredientThrowsNotFoundWhenProductDoesNotExist() {
    UUID productId = UUID.randomUUID();

    when(productRepository.findById(productId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                recipeService.findRecipesByIngredient(
                    User.builder().id(UUID.randomUUID()).build(), productId))
        .isInstanceOf(ProductNotFoundException.class);

    verifyNoInteractions(recipeRepository);
  }
}
