package fr.stockshop.stock_api.shoppinglist.controller;

import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListCategoryGroupResponse;
import fr.stockshop.stock_api.shoppinglist.service.ShoppingListService;
import fr.stockshop.stock_api.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
}
