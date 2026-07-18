package fr.stockshop.stock_api.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.stockshop.stock_api.user.entity.RefreshToken;
import fr.stockshop.stock_api.user.entity.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByToken(String token);

	void deleteByUser(User user);
}

