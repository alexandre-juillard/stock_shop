package fr.stockshop.stock_api.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.recipe.dto.RecipeResponse;
import fr.stockshop.stock_api.recipe.entity.Recipe;
import fr.stockshop.stock_api.recipe.repository.RecipeRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

  @Mock private RecipeRepository recipeRepository;
  @Mock private ProductRepository productRepository;

  @InjectMocks private RecipeService recipeService;

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
