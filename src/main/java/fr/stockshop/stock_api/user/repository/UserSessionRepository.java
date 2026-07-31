package fr.stockshop.stock_api.user.repository;

import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.entity.UserSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

  Optional<UserSession> findByTokenHash(String tokenHash);

  void deleteByUser(User user);
}
