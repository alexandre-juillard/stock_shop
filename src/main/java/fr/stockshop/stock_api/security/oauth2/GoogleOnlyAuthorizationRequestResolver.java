package fr.stockshop.stock_api.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Restreint le déclenchement du flux d'autorisation OAuth2 au seul GET /api/auth/oauth2/google.
 *
 * <p>Sans cela, le résolveur par défaut de Spring Security traite toute requête sous
 * /api/auth/oauth2/* comme une demande d'autorisation en interprétant le dernier segment comme un
 * registrationId, et lève une erreur si aucun provider ne correspond — ce qui casse nos propres
 * endpoints REST voisins (/api/auth/oauth2/callback, /api/auth/oauth2/link-decision).
 */
@Component
public class GoogleOnlyAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

  private static final String AUTHORIZATION_REQUEST_BASE_URI = "/api/auth/oauth2";

  private final DefaultOAuth2AuthorizationRequestResolver delegate;
  private final RequestMatcher googleAuthorizationRequestMatcher =
      PathPatternRequestMatcher.pathPattern(
          HttpMethod.GET, AUTHORIZATION_REQUEST_BASE_URI + "/google");

  public GoogleOnlyAuthorizationRequestResolver(
      ClientRegistrationRepository clientRegistrationRepository) {
    this.delegate =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, AUTHORIZATION_REQUEST_BASE_URI);
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    return googleAuthorizationRequestMatcher.matches(request) ? delegate.resolve(request) : null;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(
      HttpServletRequest request, String clientRegistrationId) {
    return delegate.resolve(request, clientRegistrationId);
  }
}
