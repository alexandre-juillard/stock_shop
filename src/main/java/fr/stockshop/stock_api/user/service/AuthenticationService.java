package fr.stockshop.stock_api.user.service;

import fr.stockshop.stock_api.exception.AccountAlreadyActiveException;
import fr.stockshop.stock_api.exception.EmailAlreadyExistsException;
import fr.stockshop.stock_api.exception.InvalidTokenException;
import fr.stockshop.stock_api.exception.ResetTokenExpiredException;
import fr.stockshop.stock_api.exception.ResetTokenNotFoundException;
import fr.stockshop.stock_api.exception.TokenExpiredException;
import fr.stockshop.stock_api.exception.TokenNotFoundException;
import fr.stockshop.stock_api.exception.UserNotFoundException;
import fr.stockshop.stock_api.mail.EmailService;
import fr.stockshop.stock_api.security.TokenService;
import fr.stockshop.stock_api.security.jwt.JwtService;
import fr.stockshop.stock_api.user.dto.AuthResponse;
import fr.stockshop.stock_api.user.dto.ConfirmEmailRequest;
import fr.stockshop.stock_api.user.dto.ForgotPasswordRequest;
import fr.stockshop.stock_api.user.dto.LoginRequest;
import fr.stockshop.stock_api.user.dto.LoginResponse;
import fr.stockshop.stock_api.user.dto.RefreshTokenRequest;
import fr.stockshop.stock_api.user.dto.RegisterRequest;
import fr.stockshop.stock_api.user.dto.ResendConfirmationRequest;
import fr.stockshop.stock_api.user.dto.ResetPasswordRequest;
import fr.stockshop.stock_api.user.dto.UserResponse;
import fr.stockshop.stock_api.user.entity.Role;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.entity.UserSession;
import fr.stockshop.stock_api.user.mapper.UserMapper;
import fr.stockshop.stock_api.user.repository.UserRepository;
import fr.stockshop.stock_api.user.repository.UserSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gère l'inscription, la connexion, le rafraîchissement, la révocation des sessions et la
 * réinitialisation de mot de passe.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

  private final UserRepository userRepository;
  private final UserSessionRepository userSessionRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;
  private final TokenService tokenService;
  private final EmailService emailService;
  private final UserMapper userMapper;

  @Value("${security.jwt.refresh-token-expiration}")
  private long rememberMeSessionExpiration;

  @Value("${security.session.default-expiration}")
  private long defaultSessionExpiration;

  @Value("${app.mail.token-expiration:24h}")
  private Duration confirmationTokenExpiration;

  @Value("${app.mail.reset-token-expiration:1h}")
  private Duration resetTokenExpiration;

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
            .preferredLocale(LocaleContextHolder.getLocale().getLanguage())
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
            .orElseThrow(TokenNotFoundException::new);

    if (user.getConfirmationTokenExpiresAt() == null
        || user.getConfirmationTokenExpiresAt().isBefore(Instant.now())) {
      throw new TokenExpiredException();
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
            .orElseThrow(() -> new UserNotFoundException(request.email()));

    if (user.isActive()) {
      throw new AccountAlreadyActiveException(request.email());
    }

    String rawConfirmationToken = tokenService.generateToken();
    user.setConfirmationTokenHash(tokenService.hashToken(rawConfirmationToken));
    user.setConfirmationTokenExpiresAt(Instant.now().plus(confirmationTokenExpiration));
    userRepository.save(user);

    emailService.sendAccountConfirmationEmail(user, rawConfirmationToken);
  }

  @Transactional
  public LoginResponse login(LoginRequest request, String userAgent) {
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password()));

    User user =
        Objects.requireNonNull(
            (User) authentication.getPrincipal(),
            "Le principal authentifié ne peut pas être null après une authentification réussie");

    String rawRefreshToken = UUID.randomUUID().toString();
    long sessionExpiration =
        request.isRememberMe() ? rememberMeSessionExpiration : defaultSessionExpiration;

    userSessionRepository.save(
        UserSession.builder()
            .user(user)
            .tokenHash(tokenService.hashToken(rawRefreshToken))
            .expiresAt(Instant.now().plusMillis(sessionExpiration))
            .userAgent(userAgent)
            .build());

    String accessToken = jwtService.generateAccessToken(user);

    return new LoginResponse(
        accessToken,
        rawRefreshToken,
        new LoginResponse.UserSummary(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getTheme(),
            user.getExpirationAlertDays()));
  }

  // La session expirée doit rester supprimée même si l'exception qui suit provoque normalement
  // un rollback (InvalidTokenException est une RuntimeException).
  @Transactional(noRollbackFor = InvalidTokenException.class)
  public AuthResponse refresh(RefreshTokenRequest request) {
    String tokenHash = tokenService.hashToken(request.refreshToken());
    UserSession session =
        userSessionRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidTokenException("error.token.refreshInvalid"));

    if (session.getExpiresAt().isBefore(Instant.now())) {
      userSessionRepository.delete(session);
      throw new InvalidTokenException("error.token.refreshExpiredOrRevoked");
    }

    User user = session.getUser();
    Instant preservedExpiration = session.getExpiresAt();
    String userAgent = session.getUserAgent();
    userSessionRepository.delete(session);

    // Rotation : nouveau jeton opaque, même échéance absolue que la session d'origine
    // (une rotation ne doit pas permettre de prolonger indéfiniment une session).
    String rawRefreshToken = UUID.randomUUID().toString();
    userSessionRepository.save(
        UserSession.builder()
            .user(user)
            .tokenHash(tokenService.hashToken(rawRefreshToken))
            .expiresAt(preservedExpiration)
            .userAgent(userAgent)
            .build());

    String accessToken = jwtService.generateAccessToken(user);
    return new AuthResponse(
        accessToken, rawRefreshToken, "Bearer", jwtService.getAccessTokenExpiration());
  }

  @Transactional
  public void logout(String refreshToken) {
    String tokenHash = tokenService.hashToken(refreshToken);
    userSessionRepository.findByTokenHash(tokenHash).ifPresent(userSessionRepository::delete);
  }

  @Transactional
  public void forgotPassword(ForgotPasswordRequest request) {
    // Ne révèle jamais si le compte existe : la réponse HTTP est toujours 200, l'email n'est
    // envoyé que si un compte correspond.
    userRepository
        .findByEmail(request.email())
        .ifPresent(
            user -> {
              String rawResetToken = tokenService.generateToken();
              user.setResetTokenHash(tokenService.hashToken(rawResetToken));
              user.setResetTokenExpiresAt(Instant.now().plus(resetTokenExpiration));
              userRepository.save(user);

              emailService.sendPasswordResetEmail(user, rawResetToken);
            });
  }

  // La session expirée doit rester supprimée même si l'exception qui suit provoque normalement
  // un rollback (ResetTokenExpiredException est une RuntimeException).
  @Transactional(noRollbackFor = ResetTokenExpiredException.class)
  public void resetPassword(ResetPasswordRequest request) {
    String tokenHash = tokenService.hashToken(request.token());
    User user =
        userRepository
            .findByResetTokenHash(tokenHash)
            .orElseThrow(ResetTokenNotFoundException::new);

    if (user.getResetTokenExpiresAt() == null
        || user.getResetTokenExpiresAt().isBefore(Instant.now())) {
      user.setResetTokenHash(null);
      user.setResetTokenExpiresAt(null);
      userRepository.save(user);
      throw new ResetTokenExpiredException();
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setResetTokenHash(null);
    user.setResetTokenExpiresAt(null);
    userRepository.save(user);

    // Un changement de mot de passe révoque toutes les sessions actives de l'utilisateur.
    userSessionRepository.deleteByUser(user);
  }
}
