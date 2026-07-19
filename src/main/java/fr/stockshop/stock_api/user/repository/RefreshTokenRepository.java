package fr.stockshop.stock_api.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.stockshop.stock_api.user.entity.RefreshToken;
import fr.stockshop.stock_api.user.entity.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByToken(String token);

	void deleteByUser(User user);
}

