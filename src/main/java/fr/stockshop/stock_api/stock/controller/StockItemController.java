package fr.stockshop.stock_api.stock.controller;

import fr.stockshop.stock_api.stock.dto.CreateStockItemRequest;
import fr.stockshop.stock_api.stock.dto.StockItemResponse;
import fr.stockshop.stock_api.stock.dto.UpdateStockItemQuantityRequest;
import fr.stockshop.stock_api.stock.service.StockItemService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock-items")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "Consultation et gestion du stock d'ingrédients")
public class StockItemController {

  private final StockItemService stockItemService;

  @GetMapping
  @Operation(
      summary =
          "Lister le stock, trié par catégorie puis par nom " + "(filtre optionnel : expiringSoon)")
  public ResponseEntity<List<StockItemResponse>> listStockItems(
      @AuthenticationPrincipal User currentUser,
      @RequestParam(required = false) Boolean expiringSoon) {
    return ResponseEntity.ok(stockItemService.listStockItems(currentUser, expiringSoon));
  }

  @GetMapping("/expiring-soon")
  @Operation(
      summary =
          "Lister les ingrédients bientôt périmés ou périmés, triés par date d'expiration croissante")
  public ResponseEntity<List<StockItemResponse>> listExpiringSoon(
      @AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(stockItemService.listExpiringSoon(currentUser));
  }

  @PostMapping
  @Operation(summary = "Ajouter un ingrédient du catalogue personnel au stock")
  public ResponseEntity<StockItemResponse> createStockItem(
      @AuthenticationPrincipal User currentUser,
      @Valid @RequestBody CreateStockItemRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(stockItemService.createStockItem(currentUser, request));
  }

  @PatchMapping("/{id}/quantity")
  @Operation(summary = "Modifier la quantité d'un ingrédient en stock")
  public ResponseEntity<StockItemResponse> updateQuantity(
      @AuthenticationPrincipal User currentUser,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateStockItemQuantityRequest request) {
    return ResponseEntity.ok(stockItemService.updateQuantity(currentUser, id, request));
  }
}
