package fr.stockshop.stock_api.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.stockshop.stock_api.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}

