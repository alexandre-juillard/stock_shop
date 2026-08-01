package fr.stockshop.stock_api.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.stockshop.stock_api.user.dto.OAuth2LoginOutcome;
import fr.stockshop.stock_api.user.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Connexion Google réussie : crée ou relie le compte utilisateur puis renvoie directement les
 * jetons en JSON (l'API étant stateless, aucune redirection navigateur n'est utilisée ici).
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  private static final String PROVIDER_GOOGLE = "google";

  private final AuthenticationService authenticationService;
  private final ObjectMapper objectMapper = new ObjectMapper();

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

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getWriter(), outcome.requiresLink() ? outcome.linkRequired() : outcome.tokens());
  }
}
