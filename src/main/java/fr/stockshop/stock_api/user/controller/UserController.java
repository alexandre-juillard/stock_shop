package fr.stockshop.stock_api.user.controller;

import fr.stockshop.stock_api.user.dto.UpdateLocaleRequest;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Profil utilisateur", description = "Gestion des préférences du compte connecté")
public class UserController {

  private final UserService userService;

  @PatchMapping("/locale")
  @Operation(
      summary =
          "Modifier la langue préférée du compte connecté (utilisée pour les emails et les"
              + " messages traduits de l'API)")
  public ResponseEntity<Void> updateLocale(
      @AuthenticationPrincipal User currentUser, @Valid @RequestBody UpdateLocaleRequest request) {
    userService.updatePreferredLocale(currentUser, request);
    return ResponseEntity.noContent().build();
  }
}
