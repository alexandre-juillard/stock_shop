package fr.stockshop.stock_api.user.repository;

import fr.stockshop.stock_api.user.entity.OauthLinkDecision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthLinkDecisionRepository extends JpaRepository<OauthLinkDecision, UUID> {
  Optional<OauthLinkDecision> findByUser_IdAndProviderAndProviderUserId(
      UUID userId, String provider, String providerUserId);
}
