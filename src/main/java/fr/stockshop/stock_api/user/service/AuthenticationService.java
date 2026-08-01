package fr.stockshop.stock_api.user.service;

import fr.stockshop.stock_api.exception.AccountAlreadyActiveException;
import fr.stockshop.stock_api.exception.EmailAlreadyExistsException;
import fr.stockshop.stock_api.exception.InvalidLinkContextException;
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
import fr.stockshop.stock_api.user.dto.LinkDecision;
import fr.stockshop.stock_api.user.dto.LinkDecisionRequest;
import fr.stockshop.stock_api.user.dto.LoginRequest;
import fr.stockshop.stock_api.user.dto.LoginResponse;
import fr.stockshop.stock_api.user.dto.OAuth2LoginOutcome;
import fr.stockshop.stock_api.user.dto.RefreshTokenRequest;
import fr.stockshop.stock_api.user.dto.RegisterRequest;
import fr.stockshop.stock_api.user.dto.ResendConfirmationRequest;
import fr.stockshop.stock_api.user.dto.ResetPasswordRequest;
import fr.stockshop.stock_api.user.dto.UserResponse;
import fr.stockshop.stock_api.user.entity.OauthAccount;
import fr.stockshop.stock_api.user.entity.OauthLinkContext;
import fr.stockshop.stock_api.user.entity.OauthLinkDecision;
import fr.stockshop.stock_api.user.entity.Role;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.entity.UserSession;
import fr.stockshop.stock_api.user.mapper.UserMapper;
import fr.stockshop.stock_api.user.repository.OauthAccountRepository;
import fr.stockshop.stock_api.user.repository.OauthLinkContextRepository;
import fr.stockshop.stock_api.user.repository.OauthLinkDecisionRepository;
import fr.stockshop.stock_api.user.repository.UserRepository;
import fr.stockshop.stock_api.user.repository.UserSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
  private final OauthAccountRepository oauthAccountRepository;
  private final OauthLinkContextRepository oauthLinkContextRepository;
  private final OauthLinkDecisionRepository oauthLinkDecisionRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  // @Lazy : évite un cycle d'initialisation avec SecurityConfig (Spring Security résout
  // AuthenticationManager en scrutant tous les beans @EnableGlobalAuthentication, dont
  // SecurityConfig, qui dépend transitivement de ce service via les handlers OAuth2).
  @Lazy private final AuthenticationManager authenticationManager;
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

  @Value("${app.oauth2.link-context-expiration:10m}")
  private Duration linkContextExpiration;

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

    return issueTokens(user, request.isRememberMe(), userAgent);
  }

  /**
   * Connexion via un fournisseur OAuth2 (Google...). Réutilise le compte déjà lié à ce fournisseur
   * s'il existe ; sinon, si l'email correspond à un compte local déjà actif sans décision de
   * liaison connue, une confirmation est demandée (voir {@link #resolveOAuth2LinkDecision}) plutôt
   * que d'émettre des jetons directement.
   */
  @Transactional
  public OAuth2LoginOutcome loginWithOAuth2(
      String provider,
      String providerUserId,
      String email,
      String firstName,
      String lastName,
      String avatarUrl,
      String userAgent) {
    Optional<User> alreadyLinkedUser =
        oauthAccountRepository
            .findByProviderAndProviderUserId(provider, providerUserId)
            .map(OauthAccount::getUser);
    if (alreadyLinkedUser.isPresent()) {
      return OAuth2LoginOutcome.loggedIn(issueTokens(alreadyLinkedUser.get(), false, userAgent));
    }

    Optional<User> matchingLocalUser = userRepository.findByEmail(email);
    if (matchingLocalUser.isEmpty()) {
      User newUser = createOAuthUser(email, firstName, lastName, avatarUrl);
      linkOAuthAccount(newUser, provider, providerUserId, email);
      return OAuth2LoginOutcome.loggedIn(issueTokens(newUser, false, userAgent));
    }

    User localUser = matchingLocalUser.get();

    if (!localUser.isActive()) {
      // Compte pas encore activé : liaison automatique et activation, sans alerte.
      localUser.setActive(true);
      userRepository.save(localUser);
      linkOAuthAccount(localUser, provider, providerUserId, email);
      return OAuth2LoginOutcome.loggedIn(issueTokens(localUser, false, userAgent));
    }

    Optional<OauthLinkDecision> existingDecision =
        oauthLinkDecisionRepository.findByUser_IdAndProviderAndProviderUserId(
            localUser.getId(), provider, providerUserId);
    if (existingDecision.isPresent()) {
      User resolvedUser =
          applyDecision(
              existingDecision.get(),
              localUser,
              provider,
              providerUserId,
              email,
              firstName,
              lastName,
              avatarUrl);
      return OAuth2LoginOutcome.loggedIn(issueTokens(resolvedUser, false, userAgent));
    }

    String rawLinkContext = tokenService.generateToken();
    oauthLinkContextRepository.save(
        OauthLinkContext.builder()
            .tokenHash(tokenService.hashToken(rawLinkContext))
            .targetUser(localUser)
            .provider(provider)
            .providerUserId(providerUserId)
            .providerEmail(email)
            .firstName(firstName)
            .lastName(lastName)
            .avatarUrl(avatarUrl)
            .expiresAt(Instant.now().plus(linkContextExpiration))
            .build());

    return OAuth2LoginOutcome.linkRequired(rawLinkContext);
  }

  /**
   * Résout une proposition de liaison de compte OAuth2 (POST /api/auth/oauth2/link-decision) : lie
   * le compte tiers au compte local existant, ou crée un compte indépendant si l'utilisateur refuse
   * la liaison. Le linkContext est à usage unique et supprimé après résolution.
   */
  @Transactional(noRollbackFor = InvalidLinkContextException.class)
  public LoginResponse resolveOAuth2LinkDecision(LinkDecisionRequest request, String userAgent) {
    String tokenHash = tokenService.hashToken(request.linkContext());
    OauthLinkContext context =
        oauthLinkContextRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(InvalidLinkContextException::new);

    if (context.getExpiresAt().isBefore(Instant.now())) {
      oauthLinkContextRepository.delete(context);
      throw new InvalidLinkContextException();
    }

    User targetUser = context.getTargetUser();
    boolean linked = request.decision() == LinkDecision.LINK;

    User resultUser;
    if (linked) {
      linkOAuthAccount(
          targetUser,
          context.getProvider(),
          context.getProviderUserId(),
          context.getProviderEmail());
      resultUser = targetUser;
    } else {
      resultUser =
          createIndependentOAuthUser(
              context.getProvider(),
              context.getProviderUserId(),
              context.getProviderEmail(),
              context.getFirstName(),
              context.getLastName(),
              context.getAvatarUrl());
    }

    oauthLinkDecisionRepository.save(
        OauthLinkDecision.builder()
            .user(targetUser)
            .provider(context.getProvider())
            .providerUserId(context.getProviderUserId())
            .linked(linked)
            .build());
    oauthLinkContextRepository.delete(context);

    return issueTokens(resultUser, false, userAgent);
  }

  private User applyDecision(
      OauthLinkDecision decision,
      User localUser,
      String provider,
      String providerUserId,
      String email,
      String firstName,
      String lastName,
      String avatarUrl) {
    if (decision.isLinked()) {
      linkOAuthAccount(localUser, provider, providerUserId, email);
      return localUser;
    }
    return createIndependentOAuthUser(
        provider, providerUserId, email, firstName, lastName, avatarUrl);
  }

  /**
   * Crée un compte OAuth2 totalement indépendant d'un compte local existant de même email. Comme
   * {@code users.email} est unique en base, cette adresse ne peut pas être réutilisée telle quelle
   * pour un second compte : un alias dérivé est utilisé pour la connexion, tandis que l'email réel
   * du fournisseur reste tracé dans {@code oauth_accounts.provider_email}.
   */
  private User createIndependentOAuthUser(
      String provider,
      String providerUserId,
      String providerEmail,
      String firstName,
      String lastName,
      String avatarUrl) {
    User newUser =
        createOAuthUser(
            buildIndependentAccountEmail(providerEmail, provider, providerUserId),
            firstName,
            lastName,
            avatarUrl);
    linkOAuthAccount(newUser, provider, providerUserId, providerEmail);
    return newUser;
  }

  private String buildIndependentAccountEmail(
      String providerEmail, String provider, String providerUserId) {
    int atIndex = providerEmail.indexOf('@');
    String alias = provider + "-" + providerUserId;
    return atIndex < 0
        ? alias + "@oauth.local"
        : providerEmail.substring(0, atIndex) + "+" + alias + providerEmail.substring(atIndex);
  }

  private User createOAuthUser(String email, String firstName, String lastName, String avatarUrl) {
    return userRepository.save(
        User.builder()
            .email(email)
            .firstName(firstName != null ? firstName : email)
            .lastName(lastName != null ? lastName : "")
            .avatarUrl(avatarUrl)
            .role(Role.USER)
            .active(true)
            .emailConfirmedAt(Instant.now())
            .preferredLocale(LocaleContextHolder.getLocale().getLanguage())
            .build());
  }

  private void linkOAuthAccount(
      User user, String provider, String providerUserId, String providerEmail) {
    oauthAccountRepository.save(
        OauthAccount.builder()
            .user(user)
            .provider(provider)
            .providerUserId(providerUserId)
            .providerEmail(providerEmail)
            .build());
  }

  private LoginResponse issueTokens(User user, boolean rememberMe, String userAgent) {
    String rawRefreshToken = UUID.randomUUID().toString();
    long sessionExpiration = rememberMe ? rememberMeSessionExpiration : defaultSessionExpiration;

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
