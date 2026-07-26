package fr.stockshop.stock_api.security;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Renvoie une réponse JSON homogène (403) lorsqu'un utilisateur authentifié n'a pas les droits
 * nécessaires pour accéder à une ressource.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    ApiError error =
        new ApiError(
            Instant.now(),
            HttpServletResponse.SC_FORBIDDEN,
            "Forbidden",
            "Vous n'avez pas les droits nécessaires pour accéder à cette ressource",
            request.getRequestURI(),
            Map.of());
    objectMapper.writeValue(response.getWriter(), error);
  }
}
