package fr.stockshop.stock_api.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Utilisateur de l'application, également utilisé par Spring Security comme {@link UserDetails}
 * pour l'authentification.
 *
 * <p>Le mapping suit le schéma métier "Stock &amp; Shop" (PK UUID, confirmation d'email, comptes
 * OAuth2...). La colonne {@code role} est une extension technique nécessaire à la gestion des
 * permissions Spring Security.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

  @Serial private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private Role role = Role.USER;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private boolean active = false;

  @Column(name = "email_confirmed_at")
  private Instant emailConfirmedAt;

  @Column(name = "confirmation_token_hash", length = 255)
  private String confirmationTokenHash;

  @Column(name = "confirmation_token_expires_at")
  private Instant confirmationTokenExpiresAt;

  @Column(name = "reset_token_hash", length = 255)
  private String resetTokenHash;

  @Column(name = "reset_token_expires_at")
  private Instant resetTokenExpiresAt;

  @Column(name = "preferred_locale", nullable = false, length = 5)
  @Builder.Default
  private String preferredLocale = "fr";

  @Column(name = "expiration_alert_days", nullable = false)
  @Builder.Default
  private int expirationAlertDays = 3;

  @Column(nullable = false, length = 10)
  @Builder.Default
  private String theme = "light";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }
}
