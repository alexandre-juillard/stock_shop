package fr.stockshop.stock_api.notification.controller;

import fr.stockshop.stock_api.notification.dto.RegisterPushTokenRequest;
import fr.stockshop.stock_api.notification.service.PushTokenService;
import fr.stockshop.stock_api.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push-tokens")
@RequiredArgsConstructor
@Tag(
    name = "Notifications push",
    description = "Enregistrement des tokens push (FCM/APNs) des appareils de l'utilisateur")
public class PushTokenController {

  private final PushTokenService pushTokenService;

  @PostMapping
  @Operation(summary = "Enregistrer ou mettre à jour le token push de l'appareil courant")
  public ResponseEntity<Void> registerToken(
      @AuthenticationPrincipal User currentUser,
      @Valid @RequestBody RegisterPushTokenRequest request) {
    boolean created = pushTokenService.registerToken(currentUser, request);
    return created
        ? ResponseEntity.status(HttpStatus.CREATED).build()
        : ResponseEntity.ok().build();
  }

  @DeleteMapping("/{token}")
  @Operation(summary = "Désinscrire un token push (ex : déconnexion de l'appareil)")
  public ResponseEntity<Void> unregisterToken(
      @AuthenticationPrincipal User currentUser, @PathVariable String token) {
    pushTokenService.unregisterToken(currentUser, token);
    return ResponseEntity.noContent().build();
  }
}
