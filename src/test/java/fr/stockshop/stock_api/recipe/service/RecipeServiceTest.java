package fr.stockshop.stock_api.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.RecipeIngredientProductNotInStockException;
import fr.stockshop.stock_api.exception.RecipeNotFoundException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.quantity.entity.QuantityType;
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
import fr.stockshop.stock_api.recipe.entity.Recipe;
import fr.stockshop.stock_api.recipe.entity.RecipeIngredient;
import fr.stockshop.stock_api.recipe.repository.RecipeIngredientRepository;
import fr.stockshop.stock_api.recipe.repository.RecipeRepository;
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
        recipeIngredientRepository, productRepository, quantityUnitRepository, stockItemRepository);
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
