package fr.stockshop.stock_api.product.controller;

import fr.stockshop.stock_api.product.dto.CreateProductRequest;
import fr.stockshop.stock_api.product.dto.ProductPhotoResponse;
import fr.stockshop.stock_api.product.dto.ProductPhotoUploadRequest;
import fr.stockshop.stock_api.product.dto.ProductResponse;
import fr.stockshop.stock_api.product.dto.UpdateProductRequest;
import fr.stockshop.stock_api.product.service.ProductService;
import fr.stockshop.stock_api.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

  @DeleteMapping("/{id}")
  @Operation(summary = "Supprimer un ingrédient du catalogue personnel")
  public ResponseEntity<Void> deleteProduct(
      @AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
    productService.deleteProduct(currentUser, id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Uploader ou remplacer la photo d'un ingrédient")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      content =
          @Content(
              mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
              schema = @Schema(implementation = ProductPhotoUploadRequest.class)))
  public ResponseEntity<ProductPhotoResponse> uploadProductPhoto(
      @AuthenticationPrincipal User currentUser,
      @PathVariable UUID id,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(productService.uploadPhoto(currentUser, id, file));
  }

  @DeleteMapping("/{id}/photo")
  @Operation(summary = "Supprimer la photo d'un ingrédient")
  public ResponseEntity<Void> deleteProductPhoto(
      @AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
    productService.deletePhoto(currentUser, id);
    return ResponseEntity.noContent().build();
  }
}
