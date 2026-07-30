package fr.stockshop.stock_api.user.repository;

import fr.stockshop.stock_api.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  Optional<User> findByConfirmationTokenHash(String confirmationTokenHash);
}
