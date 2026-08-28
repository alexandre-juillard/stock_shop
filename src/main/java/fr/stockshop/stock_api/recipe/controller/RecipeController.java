package fr.stockshop.stock_api.recipe.controller;

import fr.stockshop.stock_api.recipe.dto.CreateRecipeIngredientRequest;
import fr.stockshop.stock_api.recipe.dto.CreateRecipeRequest;
import fr.stockshop.stock_api.recipe.dto.RecipeDetailResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeIngredientResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeSummaryResponse;
import fr.stockshop.stock_api.recipe.dto.UpdateRecipeIngredientRequest;
import fr.stockshop.stock_api.recipe.dto.UpdateRecipeRequest;
import fr.stockshop.stock_api.recipe.service.RecipeService;
import fr.stockshop.stock_api.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
@Tag(name = "Recettes", description = "Creation et consultation des recettes")
public class RecipeController {

  private final RecipeService recipeService;

  @PostMapping
  @Operation(summary = "Creer une recette")
  public ResponseEntity<RecipeResponse> createRecipe(
      @AuthenticationPrincipal User currentUser, @Valid @RequestBody CreateRecipeRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(recipeService.createRecipe(currentUser, request));
  }

  @GetMapping
  @Operation(summary = "Lister les recettes de l'utilisateur")
  public ResponseEntity<List<RecipeSummaryResponse>> listRecipes(
      @AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(recipeService.listRecipes(currentUser));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consulter le detail d'une recette")
  public ResponseEntity<RecipeDetailResponse> getRecipe(
      @AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
    return ResponseEntity.ok(recipeService.getRecipe(currentUser, id));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Modifier le nom d'une recette")
  public ResponseEntity<RecipeResponse> updateRecipe(
      @AuthenticationPrincipal User currentUser,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRecipeRequest request) {
    return ResponseEntity.ok(recipeService.updateRecipe(currentUser, id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Supprimer une recette")
  public ResponseEntity<Void> deleteRecipe(
      @AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
    recipeService.deleteRecipe(currentUser, id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/ingredients")
  @Operation(summary = "Ajouter un ingredient a une recette")
  public ResponseEntity<RecipeIngredientResponse> addIngredient(
      @AuthenticationPrincipal User currentUser,
      @PathVariable UUID id,
      @Valid @RequestBody CreateRecipeIngredientRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(recipeService.addIngredient(currentUser, id, request));
  }

  @PutMapping("/{id}/ingredients/{productId}")
  @Operation(summary = "Modifier un ingredient d'une recette")
  public ResponseEntity<RecipeIngredientResponse> updateIngredient(
      @AuthenticationPrincipal User currentUser,
      @PathVariable UUID id,
      @PathVariable UUID productId,
      @Valid @RequestBody UpdateRecipeIngredientRequest request) {
    return ResponseEntity.ok(recipeService.updateIngredient(currentUser, id, productId, request));
  }

  @DeleteMapping("/{id}/ingredients/{productId}")
  @Operation(summary = "Supprimer un ingredient d'une recette")
  public ResponseEntity<Void> deleteIngredient(
      @AuthenticationPrincipal User currentUser,
      @PathVariable UUID id,
      @PathVariable UUID productId) {
    recipeService.deleteIngredient(currentUser, id, productId);
    return ResponseEntity.noContent().build();
  }
}
