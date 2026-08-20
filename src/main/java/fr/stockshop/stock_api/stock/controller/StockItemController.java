package fr.stockshop.stock_api.stock.controller;

import fr.stockshop.stock_api.stock.dto.StockItemResponse;
import fr.stockshop.stock_api.stock.service.StockItemService;
import fr.stockshop.stock_api.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock-items")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "Consultation du stock d'ingrédients")
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
}
