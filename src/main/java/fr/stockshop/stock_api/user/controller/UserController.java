package fr.stockshop.stock_api.user.controller;

import fr.stockshop.stock_api.user.dto.AvatarResponse;
import fr.stockshop.stock_api.user.dto.UpdateLocaleRequest;
import fr.stockshop.stock_api.user.dto.UpdateProfileRequest;
import fr.stockshop.stock_api.user.dto.UpdateSettingsRequest;
import fr.stockshop.stock_api.user.dto.UserProfileResponse;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Profil utilisateur", description = "Gestion des préférences du compte connecté")
public class UserController {

  private final UserService userService;

  @GetMapping
  @Operation(summary = "Consulter le profil du compte connecté")
  public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(userService.getProfile(currentUser));
  }

  @PutMapping
  @Operation(summary = "Modifier le profil du compte connecté (champs fournis uniquement)")
  public ResponseEntity<UserProfileResponse> updateProfile(
      @AuthenticationPrincipal User currentUser, @Valid @RequestBody UpdateProfileRequest request) {
    return ResponseEntity.ok(userService.updateProfile(currentUser, request));
  }

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

  @PutMapping("/settings")
  @Operation(summary = "Modifier le thème et le délai d'alerte d'expiration du compte connecté")
  public ResponseEntity<UserProfileResponse> updateSettings(
      @AuthenticationPrincipal User currentUser,
      @Valid @RequestBody UpdateSettingsRequest request) {
    return ResponseEntity.ok(userService.updateSettings(currentUser, request));
  }

  @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Uploader un avatar pour le compte connecté")
  public ResponseEntity<AvatarResponse> uploadAvatar(
      @AuthenticationPrincipal User currentUser, @RequestPart("file") MultipartFile fichier) {
    return ResponseEntity.ok(userService.uploadAvatar(currentUser, fichier));
  }

  @DeleteMapping("/avatar")
  @Operation(summary = "Supprimer l'avatar du compte connecté")
  public ResponseEntity<Void> deleteAvatar(@AuthenticationPrincipal User currentUser) {
    userService.deleteAvatar(currentUser);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping
  @Operation(summary = "Supprimer définitivement le compte connecté et toutes ses données")
  public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal User currentUser) {
    userService.deleteAccount(currentUser);
    return ResponseEntity.noContent().build();
  }
}
