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

/** Compte tiers (Google...) lié à un utilisateur pour l'authentification OAuth2. */
@Entity
@Table(name = "oauth_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OauthAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 50)
  private String provider;

  @Column(name = "provider_user_id", nullable = false, length = 255)
  private String providerUserId;

  @Column(name = "provider_email", nullable = false, length = 255)
  private String providerEmail;

  @CreationTimestamp
  @Column(name = "linked_at", nullable = false, updatable = false)
  private Instant linkedAt;
}
