package fr.stockshop.stock_api.quantity.controller;

import fr.stockshop.stock_api.quantity.dto.QuantityTypeResponse;
import fr.stockshop.stock_api.quantity.dto.QuantityUnitResponse;
import fr.stockshop.stock_api.quantity.service.QuantityReferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quantity-types")
@RequiredArgsConstructor
@Tag(name = "Référentiel quantités", description = "Consultation des types et unités de quantité")
public class QuantityReferenceController {

  private final QuantityReferenceService quantityReferenceService;

  @GetMapping
  @Operation(summary = "Lister les types de quantité")
  public ResponseEntity<List<QuantityTypeResponse>> getQuantityTypes() {
    return ResponseEntity.ok(quantityReferenceService.listQuantityTypes());
  }

  @GetMapping("/{typeId}/units")
  @Operation(summary = "Lister les unités d'un type de quantité")
  public ResponseEntity<List<QuantityUnitResponse>> getUnitsByType(@PathVariable String typeId) {
    return ResponseEntity.ok(quantityReferenceService.listUnitsByType(typeId));
  }
}
