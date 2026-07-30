package fr.stockshop.stock_api.user.service;

import fr.stockshop.stock_api.exception.AccountAlreadyActiveException;
import fr.stockshop.stock_api.exception.EmailAlreadyExistsException;
import fr.stockshop.stock_api.exception.InvalidTokenException;
import fr.stockshop.stock_api.exception.TokenExpiredException;
import fr.stockshop.stock_api.exception.TokenNotFoundException;
import fr.stockshop.stock_api.exception.UserNotFoundException;
import fr.stockshop.stock_api.mail.EmailService;
import fr.stockshop.stock_api.security.TokenService;
import fr.stockshop.stock_api.security.jwt.JwtService;
import fr.stockshop.stock_api.user.dto.AuthResponse;
import fr.stockshop.stock_api.user.dto.ConfirmEmailRequest;
import fr.stockshop.stock_api.user.dto.LoginRequest;
import fr.stockshop.stock_api.user.dto.RefreshTokenRequest;
import fr.stockshop.stock_api.user.dto.RegisterRequest;
import fr.stockshop.stock_api.user.dto.ResendConfirmationRequest;
import fr.stockshop.stock_api.user.dto.UserResponse;
import fr.stockshop.stock_api.user.entity.RefreshToken;
import fr.stockshop.stock_api.user.entity.Role;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.mapper.UserMapper;
import fr.stockshop.stock_api.user.repository.RefreshTokenRepository;
import fr.stockshop.stock_api.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gère l'inscription, la connexion, le rafraîchissement et la révocation des jetons JWT. */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;
  private final TokenService tokenService;
  private final EmailService emailService;
  private final UserMapper userMapper;

  @Value("${security.jwt.refresh-token-expiration}")
  private long refreshTokenExpiration;

  @Value("${app.mail.token-expiration:24h}")
  private Duration confirmationTokenExpiration;

  @Transactional
  public UserResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new EmailAlreadyExistsException(request.email());
    }

    String rawConfirmationToken = tokenService.generateToken();

    User user =
        User.builder()
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .firstName(request.firstName())
            .lastName(request.lastName())
            .role(Role.USER)
            .active(false)
            .confirmationTokenHash(tokenService.hashToken(rawConfirmationToken))
            .confirmationTokenExpiresAt(Instant.now().plus(confirmationTokenExpiration))
            .build();
    userRepository.save(user);

    emailService.sendAccountConfirmationEmail(user, rawConfirmationToken);

    return userMapper.toResponse(user);
  }

  @Transactional
  public void confirmEmail(ConfirmEmailRequest request) {
    String tokenHash = tokenService.hashToken(request.token());
    User user =
        userRepository
            .findByConfirmationTokenHash(tokenHash)
            .orElseThrow(() -> new TokenNotFoundException("Token de confirmation introuvable"));

    if (user.getConfirmationTokenExpiresAt() == null
        || user.getConfirmationTokenExpiresAt().isBefore(Instant.now())) {
      throw new TokenExpiredException("Token de confirmation expiré");
    }

    user.setActive(true);
    user.setEmailConfirmedAt(Instant.now());
    user.setConfirmationTokenHash(null);
    user.setConfirmationTokenExpiresAt(null);
    userRepository.save(user);
  }

  @Transactional
  public void resendConfirmation(ResendConfirmationRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(
                () -> new UserNotFoundException("Utilisateur introuvable : " + request.email()));

    if (user.isActive()) {
      throw new AccountAlreadyActiveException(request.email());
    }

    String rawConfirmationToken = tokenService.generateToken();
    user.setConfirmationTokenHash(tokenService.hashToken(rawConfirmationToken));
    user.setConfirmationTokenExpiresAt(Instant.now().plus(confirmationTokenExpiration));
    userRepository.save(user);

    emailService.sendAccountConfirmationEmail(user, rawConfirmationToken);
  }

  public AuthResponse login(LoginRequest request) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.email(), request.password()));

    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(
                () ->
                    new UsernameNotFoundException("Utilisateur introuvable : " + request.email()));

    return generateAuthResponse(user);
  }

  @Transactional
  public AuthResponse refresh(RefreshTokenRequest request) {
    RefreshToken storedToken =
        refreshTokenRepository
            .findByToken(request.refreshToken())
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

    refreshTokenRepository.save(
        RefreshToken.builder()
            .token(refreshToken)
            .user(user)
            .expiryDate(Instant.now().plusMillis(refreshTokenExpiration))
            .revoked(false)
            .build());

    return new AuthResponse(
        accessToken, refreshToken, "Bearer", jwtService.getAccessTokenExpiration());
  }
}
