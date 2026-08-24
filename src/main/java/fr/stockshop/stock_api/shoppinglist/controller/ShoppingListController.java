package fr.stockshop.stock_api.shoppinglist.controller;

import fr.stockshop.stock_api.shoppinglist.dto.AddShoppingListItemRequest;
import fr.stockshop.stock_api.shoppinglist.dto.CheckShoppingListItemRequest;
import fr.stockshop.stock_api.shoppinglist.dto.CheckThresholdsResponse;
import fr.stockshop.stock_api.shoppinglist.dto.FinishShoppingListResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListCategoryGroupResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListItemResponse;
import fr.stockshop.stock_api.shoppinglist.service.ShoppingListService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shopping-list")
@RequiredArgsConstructor
@Tag(name = "Liste de courses", description = "Consultation de la liste de courses utilisateur")
public class ShoppingListController {

  private final ShoppingListService shoppingListService;

  @GetMapping
  @Operation(summary = "Consulter la liste de courses groupée par catégorie")
  public ResponseEntity<List<ShoppingListCategoryGroupResponse>> listShoppingList(
      @AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(shoppingListService.listShoppingList(currentUser));
  }

  @PostMapping("/items")
  @Operation(summary = "Ajouter manuellement un produit visible à la liste de courses")
  public ResponseEntity<ShoppingListItemResponse> addShoppingListItem(
      @AuthenticationPrincipal User currentUser,
      @Valid @RequestBody AddShoppingListItemRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(shoppingListService.addItem(currentUser, request));
  }

  @DeleteMapping("/items/{id}")
  @Operation(summary = "Retirer un article de la liste de courses")
  public ResponseEntity<Void> deleteShoppingListItem(
      @AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
    shoppingListService.deleteItem(currentUser, id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping
  @Operation(summary = "Vider toute la liste de courses")
  public ResponseEntity<Void> clearShoppingList(@AuthenticationPrincipal User currentUser) {
    shoppingListService.clearList(currentUser);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/check-thresholds")
  @Operation(summary = "Verifier les seuils bas et synchroniser automatiquement la liste")
  public ResponseEntity<CheckThresholdsResponse> checkThresholds(
      @AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(shoppingListService.checkThresholds(currentUser));
  }

  @PatchMapping("/items/{id}/check")
  @Operation(summary = "Cocher un article avec la quantite achetee")
  public ResponseEntity<ShoppingListItemResponse> checkShoppingListItem(
      @AuthenticationPrincipal User currentUser,
      @PathVariable UUID id,
      @Valid @RequestBody CheckShoppingListItemRequest request) {
    return ResponseEntity.ok(shoppingListService.checkItem(currentUser, id, request));
  }

  @PatchMapping("/items/{id}/uncheck")
  @Operation(summary = "Decocher un article de la liste")
  public ResponseEntity<ShoppingListItemResponse> uncheckShoppingListItem(
      @AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
    return ResponseEntity.ok(shoppingListService.uncheckItem(currentUser, id));
  }

  @PostMapping("/finish")
  @Operation(summary = "Finaliser les courses et mettre en stock les articles coches")
  public ResponseEntity<FinishShoppingListResponse> finishShoppingList(
      @AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(shoppingListService.finishShoppingList(currentUser));
  }
}
