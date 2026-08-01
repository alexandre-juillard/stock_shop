package fr.stockshop.stock_api.user.repository;

import fr.stockshop.stock_api.user.entity.OauthAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, UUID> {

  Optional<OauthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}
