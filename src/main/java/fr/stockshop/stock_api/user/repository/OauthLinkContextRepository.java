package fr.stockshop.stock_api.user.repository;

import fr.stockshop.stock_api.user.entity.OauthLinkContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthLinkContextRepository extends JpaRepository<OauthLinkContext, UUID> {
  Optional<OauthLinkContext> findByTokenHash(String tokenHash);
}
