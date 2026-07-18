package fr.stockshop.stock_api.user.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.stockshop.stock_api.exception.EmailAlreadyExistsException;
import fr.stockshop.stock_api.exception.InvalidTokenException;
import fr.stockshop.stock_api.security.jwt.JwtService;
import fr.stockshop.stock_api.user.dto.AuthResponse;
import fr.stockshop.stock_api.user.dto.LoginRequest;
import fr.stockshop.stock_api.user.dto.RefreshTokenRequest;
import fr.stockshop.stock_api.user.dto.RegisterRequest;
import fr.stockshop.stock_api.user.entity.RefreshToken;
import fr.stockshop.stock_api.user.entity.Role;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.repository.RefreshTokenRepository;
import fr.stockshop.stock_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Gère l'inscription, la connexion, le rafraîchissement et la révocation des jetons JWT.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;

	@Value("${security.jwt.refresh-token-expiration}")
	private long refreshTokenExpiration;

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new EmailAlreadyExistsException(request.email());
		}

		User user = User.builder()
				.email(request.email())
				.password(passwordEncoder.encode(request.password()))
				.firstName(request.firstName())
				.lastName(request.lastName())
				.role(Role.USER)
				.enabled(true)
				.build();
		userRepository.save(user);

		return generateAuthResponse(user);
	}

	public AuthResponse login(LoginRequest request) {
		authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.email(), request.password()));

		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + request.email()));

		return generateAuthResponse(user);
	}

	@Transactional
	public AuthResponse refresh(RefreshTokenRequest request) {
		RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
				.orElseThrow(() -> new InvalidTokenException("Refresh token invalide"));

		if (storedToken.isRevoked() || storedToken.getExpiryDate().isBefore(Instant.now())) {
			throw new InvalidTokenException("Refresh token expiré ou révoqué");
		}

		User user = storedToken.getUser();
		refreshTokenRepository.delete(storedToken);

		return generateAuthResponse(user);
	}

	@Transactional
	public void logout(String refreshToken) {
		refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
	}

	private AuthResponse generateAuthResponse(User user) {
		String accessToken = jwtService.generateAccessToken(user);
		String refreshToken = jwtService.generateRefreshToken(user);

		refreshTokenRepository.save(RefreshToken.builder()
				.token(refreshToken)
				.user(user)
				.expiryDate(Instant.now().plusMillis(refreshTokenExpiration))
				.revoked(false)
				.build());

		return new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.getAccessTokenExpiration());
	}
}

