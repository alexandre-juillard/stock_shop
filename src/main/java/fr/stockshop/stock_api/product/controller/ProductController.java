package fr.stockshop.stock_api.product.controller;

import fr.stockshop.stock_api.product.dto.CreateProductRequest;
import fr.stockshop.stock_api.product.dto.ProductResponse;
import fr.stockshop.stock_api.product.service.ProductService;
import fr.stockshop.stock_api.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Produits", description = "Gestion du catalogue personnel d'ingrédients")
public class ProductController {

  private final ProductService productService;

  @PostMapping
  @Operation(summary = "Créer un ingrédient dans le catalogue personnel")
  public ResponseEntity<ProductResponse> createProduct(
      @AuthenticationPrincipal User currentUser, @Valid @RequestBody CreateProductRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(productService.createProduct(currentUser, request));
  }
}
