package fr.stockshop.stock_api.category.controller;

import fr.stockshop.stock_api.category.dto.CategoryResponse;
import fr.stockshop.stock_api.category.dto.CreateCategoryRequest;
import fr.stockshop.stock_api.category.dto.UpdateCategoryRequest;
import fr.stockshop.stock_api.category.service.CategoryService;
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
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Catégories", description = "Gestion des catégories d'ingrédients")
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  @Operation(summary = "Lister les catégories du compte connecté")
  public ResponseEntity<List<CategoryResponse>> getCategories(
      @AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(categoryService.listCategories(currentUser));
  }

  @PostMapping
  @Operation(summary = "Créer une catégorie")
  public ResponseEntity<CategoryResponse> createCategory(
      @AuthenticationPrincipal User currentUser,
      @Valid @RequestBody CreateCategoryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(categoryService.createCategory(currentUser, request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Modifier une catégorie")
  public ResponseEntity<CategoryResponse> updateCategory(
      @AuthenticationPrincipal User currentUser,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCategoryRequest request) {
    return ResponseEntity.ok(categoryService.updateCategory(currentUser, id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Supprimer une catégorie")
  public ResponseEntity<Void> deleteCategory(
      @AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
    categoryService.deleteCategory(currentUser, id);
    return ResponseEntity.noContent().build();
  }
}
