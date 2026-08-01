package fr.stockshop.stock_api.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.stockshop.stock_api.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Échec de connexion Google (code d'autorisation ou état invalide/corrompu) : renvoie une erreur
 * JSON 400 homogène plutôt que la redirection par défaut de Spring Security.
 */
@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    ApiError error =
        new ApiError(
            Instant.now(),
            HttpServletResponse.SC_BAD_REQUEST,
            "Bad Request",
            "Connexion Google invalide ou expirée, veuillez réessayer",
            request.getRequestURI(),
            Map.of());
    objectMapper.writeValue(response.getWriter(), error);
  }
}
