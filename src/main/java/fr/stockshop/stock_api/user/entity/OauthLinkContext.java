package fr.stockshop.stock_api.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Proposition de liaison en attente entre un compte OAuth2 (Google...) et un compte local existant
 * de même email. Le jeton associé est à usage unique et expire rapidement (voir {@code
 * app.oauth2.link-context-expiration}).
 */
@Entity
@Table(name = "oauth_link_contexts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OauthLinkContext {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "token_hash", nullable = false, length = 255)
  private String tokenHash;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "target_user_id", nullable = false)
  private User targetUser;

  @Column(nullable = false, length = 50)
  private String provider;

  @Column(name = "provider_user_id", nullable = false, length = 255)
  private String providerUserId;

  @Column(name = "provider_email", nullable = false, length = 255)
  private String providerEmail;

  @Column(name = "first_name", length = 100)
  private String firstName;

  @Column(name = "last_name", length = 100)
  private String lastName;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
