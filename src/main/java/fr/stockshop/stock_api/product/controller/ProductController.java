package fr.stockshop.stock_api.product.controller;

import fr.stockshop.stock_api.product.dto.CreateProductRequest;
import fr.stockshop.stock_api.product.dto.ProductResponse;
import fr.stockshop.stock_api.product.dto.UpdateProductRequest;
import fr.stockshop.stock_api.product.service.ProductService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Produits", description = "Gestion du catalogue personnel d'ingrédients")
public class ProductController {

  private final ProductService productService;

  @GetMapping
  @Operation(
      summary =
          "Lister les ingrédients du catalogue personnel (filtres optionnels : categoryId, visible)")
  public ResponseEntity<List<ProductResponse>> listProducts(
      @AuthenticationPrincipal User currentUser,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) Boolean visible) {
    return ResponseEntity.ok(productService.listProducts(currentUser, categoryId, visible));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consulter le détail d'un ingrédient")
  public ResponseEntity<ProductResponse> getProduct(
      @AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
    return ResponseEntity.ok(productService.getProduct(currentUser, id));
  }

  @PostMapping
  @Operation(summary = "Créer un ingrédient dans le catalogue personnel")
  public ResponseEntity<ProductResponse> createProduct(
      @AuthenticationPrincipal User currentUser, @Valid @RequestBody CreateProductRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(productService.createProduct(currentUser, request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Modifier le nom et/ou la catégorie d'un ingrédient")
  public ResponseEntity<ProductResponse> updateProduct(
      @AuthenticationPrincipal User currentUser,
      @PathVariable UUID id,
      @RequestBody UpdateProductRequest request) {
    return ResponseEntity.ok(productService.updateProduct(currentUser, id, request));
  }
}
