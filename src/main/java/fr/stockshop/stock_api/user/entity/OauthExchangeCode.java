package fr.stockshop.stock_api.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Code d'échange à usage unique remis à l'app mobile lors de la redirection consécutive à une
 * connexion OAuth2 (Google...). Le résultat métier complet ({@code
 * fr.stockshop.stock_api.user.dto.OAuth2LoginOutcome}, sérialisé en JSON) est conservé côté serveur
 * le temps que l'app l'échange via GET /api/auth/oauth2/exchange, plutôt que d'inclure des jetons
 * sensibles directement dans l'URL du deep link.
 */
@Entity
@Table(name = "oauth_exchange_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OauthExchangeCode {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "code_hash", nullable = false, length = 255)
  private String codeHash;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
