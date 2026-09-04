package fr.stockshop.stock_api.security.oauth2;

import fr.stockshop.stock_api.user.dto.OAuth2LoginOutcome;
import fr.stockshop.stock_api.user.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Connexion Google réussie : crée ou relie le compte utilisateur, puis redirige vers le deep link
 * de l'app mobile stock-mobile avec un code d'échange à usage unique (voir {@link
 * OAuth2ExchangeCodeService}). L'API étant stateless et consommée uniquement par un client natif,
 * une WebView/navigateur système ne peut pas lire un JSON renvoyé directement dans la réponse HTTP
 * de ce callback : l'app échange ensuite ce code contre les jetons via GET
 * /api/auth/oauth2/exchange.
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  private static final String PROVIDER_GOOGLE = "google";

  private final AuthenticationService authenticationService;
  private final OAuth2ExchangeCodeService exchangeCodeService;

  @Value("${app.oauth2.mobile-redirect-uri}")
  private String mobileRedirectUri;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    Object principal = ((OAuth2AuthenticationToken) authentication).getPrincipal();
    if (!(principal instanceof OidcUser oidcUser)) {
      throw new IllegalStateException("Principal OAuth2 inattendu : " + principal);
    }

    OAuth2LoginOutcome outcome =
        authenticationService.loginWithOAuth2(
            PROVIDER_GOOGLE,
            oidcUser.getSubject(),
            oidcUser.getEmail(),
            oidcUser.getGivenName(),
            oidcUser.getFamilyName(),
            oidcUser.getPicture(),
            request.getHeader("User-Agent"));

    String exchangeCode = exchangeCodeService.create(outcome);
    String redirectUrl =
        UriComponentsBuilder.fromUriString(mobileRedirectUri)
            .queryParam("code", exchangeCode)
            .build()
            .toUriString();
    response.sendRedirect(redirectUrl);
  }
}
