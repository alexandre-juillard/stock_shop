package fr.stockshop.stock_api.recipe.controller;

import fr.stockshop.stock_api.recipe.dto.CreateRecipeRequest;
import fr.stockshop.stock_api.recipe.dto.RecipeDetailResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeResponse;
import fr.stockshop.stock_api.recipe.dto.RecipeSummaryResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
