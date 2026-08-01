package fr.stockshop.stock_api.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2AuthenticationFailureHandlerTest {

  private final OAuth2AuthenticationFailureHandler handler =
      new OAuth2AuthenticationFailureHandler();

  @Test
  void writesBadRequestJsonBodyOnAuthenticationFailure() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/auth/oauth2/callback");
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    handler.onAuthenticationFailure(
        request,
        response,
        new OAuth2AuthenticationException(
            new OAuth2Error("authorization_request_not_found"), "état corrompu"));

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    assertThat(body.toString()).contains("\"status\":400").contains("/api/auth/oauth2/callback");
  }
}
