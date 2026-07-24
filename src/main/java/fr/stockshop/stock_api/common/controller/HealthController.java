package fr.stockshop.stock_api.common.controller;

import fr.stockshop.stock_api.common.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de santé applicatif simple, public, indépendant de la configuration plus riche de Spring
 * Boot Actuator ({@code /actuator/health}).
 */
@RestController
@Tag(name = "Santé", description = "Vérification de la disponibilité de l'API")
public class HealthController {

  @GetMapping("/api/health")
  @Operation(summary = "Vérifier que l'API est disponible")
  public HealthResponse health() {
    return new HealthResponse("UP");
  }
}
