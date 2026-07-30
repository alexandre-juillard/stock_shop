package fr.stockshop.stock_api.user.controller;

import fr.stockshop.stock_api.user.dto.AuthResponse;
import fr.stockshop.stock_api.user.dto.ConfirmEmailRequest;
import fr.stockshop.stock_api.user.dto.LoginRequest;
import fr.stockshop.stock_api.user.dto.RefreshTokenRequest;
import fr.stockshop.stock_api.user.dto.RegisterRequest;
import fr.stockshop.stock_api.user.dto.ResendConfirmationRequest;
import fr.stockshop.stock_api.user.dto.UserResponse;
import fr.stockshop.stock_api.user.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Inscription, connexion et gestion des jetons JWT")
public class AuthController {

  private final AuthenticationService authenticationService;

  @PostMapping("/register")
  @Operation(
      summary =
          "Créer un compte utilisateur (rôle USER par défaut, inactif jusqu'à confirmation par email)")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authenticationService.register(request));
  }

  @PostMapping("/confirm-email")
  @Operation(summary = "Activer un compte à partir du token de confirmation reçu par email")
  public ResponseEntity<Void> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
    authenticationService.confirmEmail(request);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/resend-confirmation")
  @Operation(summary = "Renvoyer un nouvel email de confirmation de compte")
  public ResponseEntity<Void> resendConfirmation(
      @Valid @RequestBody ResendConfirmationRequest request) {
    authenticationService.resendConfirmation(request);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/login")
  @Operation(summary = "S'authentifier et obtenir un access token + refresh token")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authenticationService.login(request));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Échanger un refresh token valide contre une nouvelle paire de jetons")
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(authenticationService.refresh(request));
  }

  @PostMapping("/logout")
  @Operation(summary = "Révoquer un refresh token (déconnexion)")
  public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
    authenticationService.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }
}
