package fr.stockshop.stock_api.security.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

/**
 * Persiste la requête d'autorisation OAuth2 (state, nonce...) dans un cookie plutôt qu'en session
 * HTTP, afin de conserver une API stateless entre la redirection vers Google et le retour sur le
 * callback.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  static final String COOKIE_NAME = "oauth2_auth_request";
  private static final int COOKIE_MAX_AGE_SECONDS = 180;

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return findCookie(request).flatMap(this::tryDeserialize).orElse(null);
  }

  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authorizationRequest == null) {
      deleteCookie(response);
      return;
    }
    Cookie cookie = new Cookie(COOKIE_NAME, serialize(authorizationRequest));
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
    response.addCookie(cookie);
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
    deleteCookie(response);
    return authorizationRequest;
  }

  private Optional<Cookie> findCookie(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return Optional.empty();
    }
    return Arrays.stream(request.getCookies())
        .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
        .findFirst();
  }

  private void deleteCookie(HttpServletResponse response) {
    Cookie cookie = new Cookie(COOKIE_NAME, "");
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }

  private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
    return Base64.getUrlEncoder()
        .encodeToString(SerializationUtils.serialize(authorizationRequest));
  }

  // Un cookie corrompu/altéré ne doit jamais faire planter la requête : il est simplement traité
  // comme absent, ce qui amène Spring Security à lever une OAuth2AuthenticationException standard
  // ("état corrompu") plutôt qu'une erreur 500.
  private Optional<OAuth2AuthorizationRequest> tryDeserialize(Cookie cookie) {
    try {
      return Optional.ofNullable(
          (OAuth2AuthorizationRequest)
              SerializationUtils.deserialize(Base64.getUrlDecoder().decode(cookie.getValue())));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }
}
