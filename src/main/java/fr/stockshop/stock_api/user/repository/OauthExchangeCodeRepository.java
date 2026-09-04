package fr.stockshop.stock_api.user.repository;

import fr.stockshop.stock_api.user.entity.OauthExchangeCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthExchangeCodeRepository extends JpaRepository<OauthExchangeCode, UUID> {

  Optional<OauthExchangeCode> findByCodeHash(String codeHash);
}
