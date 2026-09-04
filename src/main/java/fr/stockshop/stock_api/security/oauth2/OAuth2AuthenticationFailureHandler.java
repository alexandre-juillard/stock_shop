package fr.stockshop.stock_api.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Échec de connexion Google (code d'autorisation ou état invalide/corrompu) : redirige vers le deep
 * link de l'app mobile stock-mobile avec un paramètre d'erreur, plutôt que de renvoyer un JSON
 * directement dans la réponse HTTP (illisible depuis la WebView/navigateur système ayant initié le
 * flux).
 */
@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

  private final String mobileRedirectUri;

  public OAuth2AuthenticationFailureHandler(
      @Value("${app.oauth2.mobile-redirect-uri}") String mobileRedirectUri) {
    this.mobileRedirectUri = mobileRedirectUri;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    String redirectUrl =
        UriComponentsBuilder.fromUriString(mobileRedirectUri)
            .queryParam("error", "oauth2_failed")
            .build()
            .toUriString();
    response.sendRedirect(redirectUrl);
  }
}
